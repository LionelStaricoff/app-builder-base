package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.notification.entity.NotificationType;
import com.openlab.mx.movo.notification.service.NotificationService;
import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.entity.PayoutStatus;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.payment.service.ReservationStatusPort;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.shared.exception.EscrowException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Escrow (spec §5.3): el pago se libera cuando todos los confirmados
 * evalúan (estrella + comentario) o cuando pasan los 2 días de la ventana.
 * Liberar un pago ya reembolsado es un error; liberarlo dos veces es no-op.
 */
@Service
@RequiredArgsConstructor
public class EscrowService {

    private final ReservationStatusPort reservationStatusPort;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final com.openlab.mx.movo.payment.gateway.PaymentGateway gateway;
    private final EscrowExperiencePort experiencePort;

    @Value("${escrow.auto-release-days}")
    private long autoReleaseDays = 2;

    /**
     * @return true si la liberación ocurrió en esta llamada, false si no corresponde.
     */
    @Transactional
    public boolean tryRelease(Long reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new EscrowException("Pago no encontrado para esa reserva"));

        switch (payment.getStatus()) {
            case RELEASED:
                return false; // ya se liberó: idempotente (Review Focus #5)
            case REFUNDED_FULL, REFUNDED_PARTIAL:
                throw new EscrowException("El pago ya fue reembolsado, no se puede liberar");
            case HELD:
                break;
        }

        Long instanceId = reservationStatusPort.confirmedInstanceId(reservationId).orElse(null);
        if (instanceId == null) {
            return false;
        }
        if (!experiencePort.isInstanceCompleted(instanceId)) {
            return false;
        }
        if (!experiencePort.allConfirmedRated(instanceId) && !ventanaVencida(payment)) {
            return false;
        }

        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now(ZoneId.systemDefault()));
        payment.setPayout(payment.getAmount().subtract(payment.getCommission()));
        // Marcar como pendiente de payout al creador (Stripe Connect)
        payment.setPayoutStatus(PayoutStatus.PENDING);
        paymentRepository.save(payment);
        // Spec §4: RELEASED aplica el dinero real (capture) tras la persistencia
        // (DB source of truth). Legacy sin pi: solo contable.
        if (payment.getStripePaymentIntentId() != null) {
            gateway.capture(payment.getStripePaymentIntentId());
        }

        var creador = experiencePort.experienceCreator(instanceId).orElse(null);
        if (creador != null) {
            notificationService.notify(creador, NotificationType.PAGO_LIBERADO,
                    "Te liberamos $" + payment.getPayout() + " por "
                            + experiencePort.experienceName(instanceId),
                    "/reservations");
        }
        return true;
    }

    private boolean ventanaVencida(Payment payment) {
        return payment.getHeldAt() != null
                && !payment.getHeldAt().plusDays(autoReleaseDays).isAfter(LocalDateTime.now(ZoneId.systemDefault()));
    }
}
