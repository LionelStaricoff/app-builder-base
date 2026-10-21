package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.ConnectAccount;
import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.payment.repository.ConnectAccountRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Operaciones sobre la cuenta Stripe Connect del creador.
 *
 * <p>Vive en su propio bean (y no dentro de {@link StripeConnectService}) por
 * un motivo concreto: los métodos anotados con {@code @Transactional} solo se
 * ejecutan dentro de una transacción si se llaman a través del proxy de
 * Spring. Llamarlos por {@code this} —auto-llamada— saltea el proxy y la
 * transacción nunca se abre. Al delegar a este bean, la llamada pasa por el
 * proxy y el comportamiento transaccional es el que se espera.
 *
 * <p>Ningún método lleva {@code @Transactional} a propósito: la llamada a la
 * API de Stripe es una llamada de red y no debe quedar con una transacción de
 * base abierta encima. La escritura la abre el propio repository
 * ({@code save()} es transaccional en Spring Data), así que cada cambio de
 * estado sigue siendo atómico.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectAccountService {

    private final ConnectAccountRepository connectAccountRepository;
    private final PaymentGateway gateway;

    /**
     * Crea la cuenta Connect en Stripe y la guarda localmente.
     *
     * @param country código ISO del país de cobro, ya validado contra la lista
     *                blanca por el llamador.
     */
    public ConnectAccount createAccount(User creator, String country) {
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
     * Consulta el estado real de la cuenta Connect en Stripe y actualiza el
     * estado local. No crea cuentas: si no existe localmente es un error de
     * flujo (la creación requiere un país elegido por el usuario).
     */
    public ConnectAccount syncAccountStatus(User creator) {
        ConnectAccount account = connectAccountRepository.findByUser(creator)
                .orElseThrow(() -> new DomainException(
                        "No hay cuenta de cobro configurada todavía."));
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

    /** La cuenta Connect del creador, si ya existe. */
    public Optional<ConnectAccount> findByUser(User creator) {
        return connectAccountRepository.findByUser(creator);
    }

    /** Si el creador puede recibir pagos ahora mismo. */
    public boolean canReceivePayouts(User creator) {
        return findByUser(creator)
                .map(a -> a.getStatus() == ConnectAccountStatus.ACTIVE)
                .orElse(false);
    }
}
