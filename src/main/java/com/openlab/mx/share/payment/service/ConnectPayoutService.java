package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.ConnectAccount;
import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.entity.ConnectPayout;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PayoutStatus;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.payment.repository.ConnectAccountRepository;
import com.openlab.mx.share.payment.repository.ConnectPayoutRepository;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de desembolsos al creador vía Stripe Connect.
 * El creador reclama sus ganancias manualmente con un botón.
 * Si no tiene cuenta Connect activa, el dinero se acumula hasta que la configure.
 */
@Service
@RequiredArgsConstructor
public class ConnectPayoutService {

    private final ConnectPayoutRepository connectPayoutRepository;
    private final ConnectAccountRepository connectAccountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final StripeConnectService stripeConnectService;
    private final CurrentUser currentUser;

    /**
     * El creador reclama todos sus pagos pendientes.
     * Ejecuta un transfer batch a su cuenta Connect.
     *
     * Idempotencia: si ya existe un payout para cualquiera de los pagos pendientes,
     * se rechaza la solicitud para evitar transfers duplicados.
     */
    @Transactional
    public Map<String, Object> requestPayout() {
        User creator = currentUser.require();

        // 1. Verificar cuenta Connect activa
        ConnectAccount account = connectAccountRepository.findByUser(creator)
                .orElseThrow(() -> new DomainException("No tienes una cuenta Connect configurada"));

        if (account.getStatus() != ConnectAccountStatus.ACTIVE) {
            throw new DomainException("Tu cuenta Connect no está habilitada para recibir pagos");
        }

        // 2. Buscar pagos pendientes de payout
        List<Payment> pendingPayments = paymentRepository.findPendingPayoutsByCreator(creator);
        if (pendingPayments.isEmpty()) {
            throw new DomainException("No hay fondos disponibles para reclamar");
        }

        // 3. Verificación de idempotencia: si ya existe un payout para cualquiera
        //    de estos pagos, rechazar para evitar transfer duplicado
        for (Payment payment : pendingPayments) {
            if (connectPayoutRepository.existsByPaymentId(payment.getId())) {
                throw new DomainException("Ya existe una solicitud de payout para uno de estos pagos. Verifica tu historial.");
            }
        }

        // 4. Calcular total
        BigDecimal totalAmount = pendingPayments.stream()
                .map(Payment::getPayout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Clave de idempotencia determinística: mismo usuario + mismos pagos = misma clave
        String idempotencyKey = "payout-" + creator.getId() + "-" + pendingPayments.stream()
                .map(p -> String.valueOf(p.getId()))
                .sorted()
                .reduce((a, b) -> a + ":" + b)
                .orElse("");

        // 6. Ejecutar transfer batch a la cuenta conectada
        String transferId;
        try {
            transferId = gateway.transferToConnectedAccount(
                    account.getStripeAccountId(),
                    totalAmount,
                    pendingPayments.get(0).getReservation().getInstance().getExperience().getCurrencyCode(),
                    idempotencyKey
            );
        } catch (Exception e) {
            // Registrar el fallo para auditoría
            for (Payment payment : pendingPayments) {
                ConnectPayout failedPayout = ConnectPayout.builder()
                        .creator(creator)
                        .payment(payment)
                        .amount(payment.getPayout())
                        .commission(payment.getCommission())
                        .currency(payment.getReservation().getInstance().getExperience().getCurrencyCode())
                        .status(PayoutStatus.FAILED)
                        .failureReason(e.getMessage())
                        .idempotencyKey(idempotencyKey + "-" + payment.getId())
                        .requestedAt(LocalDateTime.now())
                        .build();
                connectPayoutRepository.save(failedPayout);
            }
            throw new DomainException("No se pudo ejecutar el transfer. Reintenta en unos minutos.");
        }

        // 7. Registrar payouts exitosos y actualizar pagos
        for (Payment payment : pendingPayments) {
            ConnectPayout payout = ConnectPayout.builder()
                    .creator(creator)
                    .payment(payment)
                    .amount(payment.getPayout())
                    .commission(payment.getCommission())
                    .currency(payment.getReservation().getInstance().getExperience().getCurrencyCode())
                    .stripeTransferId(transferId)
                    .status(PayoutStatus.COMPLETED)
                    .idempotencyKey(idempotencyKey + "-" + payment.getId())
                    .requestedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();
            connectPayoutRepository.save(payout);

            // Marcar el pago como payout completado
            payment.setPayoutStatus(PayoutStatus.COMPLETED);
            paymentRepository.save(payment);
        }

        return Map.of(
                "status", "COMPLETED",
                "amount", totalAmount,
                "transferId", transferId,
                "paymentsProcessed", pendingPayments.size()
        );
    }

    /**
     * Historial de desembolsos del creador.
     */
    public List<ConnectPayout> getPayoutHistory() {
        User creator = currentUser.require();
        return connectPayoutRepository.findByCreatorOrderByRequestedAtDesc(creator);
    }

    /**
     * Monto total pendiente de reclamar.
     */
    public BigDecimal getPendingAmount() {
        User creator = currentUser.require();
        return paymentRepository.findPendingPayoutsByCreator(creator).stream()
                .map(Payment::getPayout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
