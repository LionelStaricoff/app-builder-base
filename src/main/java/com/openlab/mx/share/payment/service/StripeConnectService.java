package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.ConnectAccount;
import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.UnsupportedPayoutCountryException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestión de cuentas Stripe Connect para creadores.
 * Crea cuentas, genera enlaces de onboarding y consulta estado real desde Stripe.
 *
 * El país de la cuenta Connect lo elige el creador, pero solo puede elegir
 * entre la lista blanca de países donde Stripe permite crear cuentas
 * (stripe.com/global). Ej.: Colombia (CO) NO está soportada — sin esta
 * validación, {@code Account.create(country=CO)} revienta con
 * {@code country_unsupported} desde la API de Stripe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeConnectService {

    private final ConnectAccountService connectAccountService;
    private final PaymentGateway gateway;

    /**
     * Claves del mapa de estado que devuelve {@link #getStatus(User)}.
     * Son el contrato con el front (Thymeleaf y el JS de cobros), asi que
     * viven aca y no repetidas como literales en cada consumidor.
     */
    public static final String KEY_HAS_CONNECTED_ACCOUNT = "hasConnectedAccount";
    public static final String KEY_STRIPE_ACCOUNT_ID = "stripeAccountId";
    public static final String KEY_STATUS = "status";
    public static final String KEY_PAYOUTS_ENABLED = "payoutsEnabled";
    public static final String KEY_ONBOARDING_REQUIRED = "onboardingRequired";
    public static final String KEY_COUNTRY = "country";

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Lista blanca de países donde Stripe permite crear cuentas Connect.
     * Configurable por entorno; ver stripe.com/global.
     */
    @Value("${app.stripe.payout-countries:MX,BR,US,CA,ES,GB,FR,DE,IT,PT,NL,BE,AT,IE,SE,DK,FI,NO,PL,CZ,AU,NZ,SG,JP,HK,AE,CH}")
    private String payoutCountriesConfig;

    /** País de cobro para el selector del front (ISO + nombre en español). */
    public record PayoutCountry(String iso, String name) {
    }

    /** ISO → nombre en español (solo presentación en el selector). */
    private static final Map<String, String> NOMBRES_PAISES = Map.ofEntries(
            Map.entry("MX", "México"), Map.entry("BR", "Brasil"), Map.entry("US", "Estados Unidos"),
            Map.entry("CA", "Canadá"), Map.entry("ES", "España"), Map.entry("GB", "Reino Unido"),
            Map.entry("FR", "Francia"), Map.entry("DE", "Alemania"), Map.entry("IT", "Italia"),
            Map.entry("PT", "Portugal"), Map.entry("NL", "Países Bajos"), Map.entry("BE", "Bélgica"),
            Map.entry("AT", "Austria"), Map.entry("IE", "Irlanda"), Map.entry("SE", "Suecia"),
            Map.entry("DK", "Dinamarca"), Map.entry("FI", "Finlandia"), Map.entry("NO", "Noruega"),
            Map.entry("PL", "Polonia"), Map.entry("CZ", "Chequia"), Map.entry("AU", "Australia"),
            Map.entry("NZ", "Nueva Zelanda"), Map.entry("SG", "Singapur"), Map.entry("JP", "Japón"),
            Map.entry("HK", "Hong Kong"), Map.entry("AE", "Emiratos Árabes Unidos"), Map.entry("CH", "Suiza"),
            // No soportados por Stripe, solo para mensajes de error amigables:
            Map.entry("CO", "Colombia"), Map.entry("AR", "Argentina"), Map.entry("CL", "Chile"),
            Map.entry("PE", "Perú"), Map.entry("EC", "Ecuador"), Map.entry("UY", "Uruguay"));

    /**
     * Genera un Account Link temporal para Hosted Onboarding.
     * El link expira: no debe almacenarse como permanente.
     *
     * <p>La cuenta se busca en {@link ConnectAccountService}: si el creador
     * todavía no tiene cuenta se crea una nueva (y solo entonces se valida el
     * país pedido; si ya tiene cuenta, el país está fijo y se ignora).
     *
     * @param countryIso país elegido por el creador (ISO 3166-1 alpha-2);
     *                   solo se usa al crear la cuenta nueva.
     */
    @Transactional
    public String generateOnboardingLink(User creator, String countryIso) {
        ConnectAccount account = connectAccountService.findByUser(creator)
                .map(existing -> {
                    log.info("[connect] cuenta existente id={} stripeId={} status={}",
                            existing.getId(), existing.getStripeAccountId(), existing.getStatus());
                    return existing;
                })
                .orElseGet(() -> connectAccountService.createAccount(creator, requireSupportedCountry(countryIso)));
        String refreshUrl = baseUrl + "/onboarding/refresh";
        String returnUrl = baseUrl + "/onboarding/complete";
        log.info("[connect] creando account link — stripeId={} refresh={} return={}",
                account.getStripeAccountId(), refreshUrl, returnUrl);
        String link = gateway.createAccountLink(account.getStripeAccountId(), refreshUrl, returnUrl);
        if (link == null || link.isBlank()) {
            // La pasarela devolvió un link vacío: si lo dejamos pasar, el
            // controller explota con NullPointerException (500 sin mensaje).
            log.warn("[connect] la pasarela devolvió un account link vacío — stripeId={}",
                    account.getStripeAccountId());
            throw new DomainException(
                    "No se pudo generar el enlace de configuración de cobros. Intentá de nuevo.");
        }
        log.info("[connect] account link OK (long={})", link.length());
        return link;
    }

    /**
     * Determina si el creador puede recibir pagos ahora mismo.
     */
    public boolean canReceivePayouts(User creator) {
        return connectAccountService.canReceivePayouts(creator);
    }

    /**
     * Devuelve el estado completo para mostrar en el frontend.
     */
    public Map<String, Object> getStatus(User creator) {
        ConnectAccount account = connectAccountService.findByUser(creator).orElse(null);
        if (account == null) {
            log.info("[connect] getStatus: sin cuenta Connect (NOT_STARTED)");
            return Map.of(
                    KEY_HAS_CONNECTED_ACCOUNT, false,
                    KEY_STATUS, ConnectAccountStatus.NOT_STARTED.name(),
                    KEY_PAYOUTS_ENABLED, false,
                    KEY_ONBOARDING_REQUIRED, true,
                    KEY_COUNTRY, ""
            );
        }
        // Sincronizar con Stripe antes de devolver
        ConnectAccount synced = connectAccountService.syncAccountStatus(creator);
        log.info("[connect] getStatus: cuenta {} → status={} payoutsEnabled={}",
                synced.getStripeAccountId(), synced.getStatus(),
                synced.getStatus() == ConnectAccountStatus.ACTIVE);
        return Map.of(
                KEY_HAS_CONNECTED_ACCOUNT, true,
                KEY_STRIPE_ACCOUNT_ID, synced.getStripeAccountId(),
                KEY_STATUS, synced.getStatus().name(),
                KEY_PAYOUTS_ENABLED, synced.getStatus() == ConnectAccountStatus.ACTIVE,
                KEY_ONBOARDING_REQUIRED, synced.getStatus() != ConnectAccountStatus.ACTIVE,
                KEY_COUNTRY, synced.getCountry() == null ? "" : synced.getCountry()
        );
    }

    // ─── Lista blanca de países de cobro ──────────────────────────────────

    /**
     * Códigos ISO de la lista blanca de países soportados por Stripe.
     */
    public List<String> supportedPayoutCountryCodes() {
        return Arrays.stream(payoutCountriesConfig.split(","))
                .map(String::trim)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .filter(s -> s.length() == 2)
                .distinct()
                .toList();
    }

    /** Lista blanca con nombre en español, para el selector del front. */
    public List<PayoutCountry> supportedPayoutCountries() {
        return supportedPayoutCountryCodes().stream()
                .map(iso -> new PayoutCountry(iso, countryName(iso)))
                .toList();
    }

    public boolean isSupportedPayoutCountry(String iso) {
        return iso != null && supportedPayoutCountryCodes()
                .contains(iso.trim().toUpperCase(Locale.ROOT));
    }

    /** Nombre en español del ISO (o el propio ISO si no lo tenemos). */
    public String countryName(String iso) {
        if (iso == null || iso.isBlank()) {
            return "";
        }
        String limpio = iso.trim().toUpperCase(Locale.ROOT);
        return NOMBRES_PAISES.getOrDefault(limpio, limpio);
    }

    /**
     * ISO preseleccionado en el selector de país de cobro: el de la cuenta
     * existente (si ya hay una), o el del perfil del usuario si Stripe lo
     * soporta; si no, ninguno (el creador debe elegir).
     */
    public String resolvePayoutCountry(User creator) {
        return connectAccountService.findByUser(creator)
                .map(ConnectAccount::getCountry)
                .orElseGet(() -> {
                    String iso = mapCountryToIso(creator.getCountry());
                    return isSupportedPayoutCountry(iso) ? iso : null;
                });
    }

    /**
     * Valida el país elegido contra la lista blanca.
     * Lanza {@link UnsupportedPayoutCountryException} (→ 400 JSON) si no está.
     */
    private String requireSupportedCountry(String countryIso) {
        String iso = countryIso == null ? "" : countryIso.trim().toUpperCase(Locale.ROOT);
        if (iso.isEmpty()) {
            throw new UnsupportedPayoutCountryException(
                    "Elegí el país de tu cuenta bancaria para continuar.");
        }
        if (!isSupportedPayoutCountry(iso)) {
            throw new UnsupportedPayoutCountryException(
                    "Stripe aún no permite cuentas de cobro en " + countryName(iso)
                            + ". Países disponibles: "
                            + supportedPayoutCountryCodes().stream()
                                    .map(this::countryName)
                                    .collect(Collectors.joining(", "))
                            + ".");
        }
        return iso;
    }

    /**
     * Mapea el país del perfil (ej. "México") a código ISO (ej. "MX").
     * Si ya es un código ISO de 2 letras, lo usa directo.
     * País desconocido → cadena vacía (sin preselección en el selector).
     */
    private String mapCountryToIso(String country) {
        if (country == null || country.isBlank()) {
            return "MX"; // default: plataforma mexicana
        }
        if (country.length() == 2) {
            return country.toUpperCase(Locale.ROOT);
        }
        // Mapeo básico de nombres comunes a ISO
        return switch (country.toLowerCase(Locale.ROOT)) {
            case "mexico", "méxico" -> "MX";
            case "argentina" -> "AR";
            case "colombia" -> "CO";
            case "chile" -> "CL";
            case "peru", "perú" -> "PE";
            case "estados unidos", "united states", "usa" -> "US";
            case "españa", "spain" -> "ES";
            case "brasil", "brazil" -> "BR";
            case "canada", "canadá" -> "CA";
            case "reino unido", "united kingdom" -> "GB";
            default -> "";
        };
    }
}
