package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.ConnectAccount;
import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.payment.repository.ConnectAccountRepository;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Gestión de cuentas Stripe Connect para creadores.
 * Crea cuentas, genera enlaces de onboarding y consulta estado real desde Stripe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeConnectService {

    private final ConnectAccountRepository connectAccountRepository;
    private final PaymentGateway gateway;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Obtiene o crea la cuenta Connect del creador.
     * Si ya existe, la devuelve. Si no, la crea en Stripe y la persiste.
     */
    @Transactional
    public ConnectAccount getOrCreateAccount(User creator) {
        return connectAccountRepository.findByUser(creator)
                .map(existing -> {
                    log.info("[connect] cuenta existente id={} stripeId={} status={}",
                            existing.getId(), existing.getStripeAccountId(), existing.getStatus());
                    return existing;
                })
                .orElseGet(() -> createAccount(creator));
    }

    /**
     * Genera un Account Link temporal para Hosted Onboarding.
     * El link expira: no debe almacenarse como permanente.
     */
    @Transactional
    public String generateOnboardingLink(User creator) {
        ConnectAccount account = getOrCreateAccount(creator);
        String refreshUrl = baseUrl + "/onboarding/refresh";
        String returnUrl = baseUrl + "/onboarding/complete";
        log.info("[connect] creando account link — stripeId={} refresh={} return={}",
                account.getStripeAccountId(), refreshUrl, returnUrl);
        String link = gateway.createAccountLink(account.getStripeAccountId(), refreshUrl, returnUrl);
        log.info("[connect] account link OK (long={})", link != null ? link.length() : 0);
        return link;
    }

    /**
     * Consulta el estado real de la cuenta Connect desde Stripe
     * y actualiza el estado local.
     */
    @Transactional
    public ConnectAccount syncAccountStatus(User creator) {
        ConnectAccount account = getOrCreateAccount(creator);
        log.info("[connect] sincronizando estado desde el gateway — stripeId={}", account.getStripeAccountId());
        PaymentGateway.ConnectAccountInfo info = gateway.retrieveConnectAccount(account.getStripeAccountId());
        log.info("[connect] gateway respondió payoutsEnabled={} chargesEnabled={} disabledReason={}",
                info.payoutsEnabled(), info.chargesEnabled(), info.disabledReason());

        if (info.payoutsEnabled() && info.chargesEnabled()) {
            account.setStatus(ConnectAccountStatus.ACTIVE);
        } else if (info.disabledReason() != null) {
            account.setStatus(ConnectAccountStatus.RESTRICTED);
        } else {
            account.setStatus(ConnectAccountStatus.ONBOARDING_INCOMPLETE);
        }
        account.setLastSyncedAt(LocalDateTime.now());
        return connectAccountRepository.save(account);
    }

    /**
     * Determina si el creador puede recibir pagos ahora mismo.
     */
    public boolean canReceivePayouts(User creator) {
        return connectAccountRepository.findByUser(creator)
                .map(a -> a.getStatus() == ConnectAccountStatus.ACTIVE)
                .orElse(false);
    }

    /**
     * Devuelve el estado completo para mostrar en el frontend.
     */
    public Map<String, Object> getStatus(User creator) {
        ConnectAccount account = connectAccountRepository.findByUser(creator).orElse(null);
        if (account == null) {
            log.info("[connect] getStatus: sin cuenta Connect (NOT_STARTED)");
            return Map.of(
                    "hasConnectedAccount", false,
                    "status", ConnectAccountStatus.NOT_STARTED.name(),
                    "payoutsEnabled", false,
                    "onboardingRequired", true
            );
        }
        // Sincronizar con Stripe antes de devolver
        ConnectAccount synced = syncAccountStatus(creator);
        log.info("[connect] getStatus: cuenta {} → status={} payoutsEnabled={}",
                synced.getStripeAccountId(), synced.getStatus(),
                synced.getStatus() == ConnectAccountStatus.ACTIVE);
        return Map.of(
                "hasConnectedAccount", true,
                "stripeAccountId", synced.getStripeAccountId(),
                "status", synced.getStatus().name(),
                "payoutsEnabled", synced.getStatus() == ConnectAccountStatus.ACTIVE,
                "onboardingRequired", synced.getStatus() != ConnectAccountStatus.ACTIVE
        );
    }

    private ConnectAccount createAccount(User creator) {
        String country = mapCountryToIso(creator.getCountry());
        log.info("[connect] creando cuenta Connect en el gateway — country={} email={} (¿Stripe configurado?){}",
                country, creator.getEmail(), gateway.getClass().getSimpleName());
        String stripeAccountId = gateway.createConnectAccount(country, creator.getEmail());
        log.info("[connect] cuenta Connect creada stripeId={}", stripeAccountId);
        ConnectAccount account = ConnectAccount.builder()
                .user(creator)
                .stripeAccountId(stripeAccountId)
                .country(country)
                .status(ConnectAccountStatus.ONBOARDING_INCOMPLETE)
                .createdAt(LocalDateTime.now())
                .build();
        return connectAccountRepository.save(account);
    }

    /**
     * Mapea el país del perfil (ej. "México") a código ISO (ej. "MX").
     * Si ya es un código ISO de 2 letras, lo usa directo.
     */
    private String mapCountryToIso(String country) {
        if (country == null || country.isBlank()) {
            return "MX"; // default
        }
        if (country.length() == 2) {
            return country.toUpperCase();
        }
        // Mapeo básico de nombres comunes a ISO
        return switch (country.toLowerCase()) {
            case "mexico", "méxico" -> "MX";
            case "argentina" -> "AR";
            case "colombia" -> "CO";
            case "chile" -> "CL";
            case "peru", "perú" -> "PE";
            case "estados unidos", "united states", "usa" -> "US";
            case "españa", "spain" -> "ES";
            case "brasil", "brazil" -> "BR";
            default -> "MX";
        };
    }
}
