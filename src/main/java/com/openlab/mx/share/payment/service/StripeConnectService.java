package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.ConnectAccount;
import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.payment.repository.ConnectAccountRepository;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
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
        return gateway.createAccountLink(account.getStripeAccountId(), refreshUrl, returnUrl);
    }

    /**
     * Consulta el estado real de la cuenta Connect desde Stripe
     * y actualiza el estado local.
     */
    @Transactional
    public ConnectAccount syncAccountStatus(User creator) {
        ConnectAccount account = getOrCreateAccount(creator);
        PaymentGateway.ConnectAccountInfo info = gateway.retrieveConnectAccount(account.getStripeAccountId());

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
            return Map.of(
                    "hasConnectedAccount", false,
                    "status", ConnectAccountStatus.NOT_STARTED.name(),
                    "payoutsEnabled", false,
                    "onboardingRequired", true
            );
        }
        // Sincronizar con Stripe antes de devolver
        ConnectAccount synced = syncAccountStatus(creator);
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
        String stripeAccountId = gateway.createConnectAccount(country, creator.getEmail());
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
