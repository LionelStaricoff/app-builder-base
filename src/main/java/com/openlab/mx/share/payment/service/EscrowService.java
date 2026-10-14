package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.service.NotificationService;
import com.openlab.mx.share.experience.entity.Comment;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.Rating;
import com.openlab.mx.share.experience.repository.CommentRepository;
import com.openlab.mx.share.experience.repository.RatingRepository;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.EscrowException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;
    private final com.openlab.mx.share.payment.gateway.PaymentGateway gateway;

    @Value("${escrow.auto-release-days}")
    private long autoReleaseDays = 2;

    /**
     * @return true si la liberación ocurrió en esta llamada, false si no corresponde.
     */
    @Transactional
    public boolean tryRelease(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new DomainException("Reserva no encontrada"));
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new EscrowException("Pago no encontrado para esa reserva"));

        switch (payment.getStatus()) {
            case RELEASED:
                return false; // ya se liberó: idempotente (Review Focus #5)
            case REFUNDED_FULL:
            case REFUNDED_PARTIAL:
                throw new EscrowException("El pago ya fue reembolsado, no se puede liberar");
            case HELD:
                break;
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMADA) {
            return false;
        }
        if (reservation.getInstance().getStatus() != InstanceStatus.COMPLETED) {
            return false;
        }
        if (!todosEvaluaron(reservation.getInstance()) && !ventanaVencida(payment)) {
            return false;
        }

        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now());
        payment.setPayout(payment.getAmount().subtract(payment.getCommission()));
        paymentRepository.save(payment);
        // Spec §4: RELEASED aplica el dinero real (capture) tras la persistencia
        // (DB source of truth). Legacy sin pi: solo contable.
        if (payment.getStripePaymentIntentId() != null) {
            gateway.capture(payment.getStripePaymentIntentId());
        }

        var creador = reservation.getInstance().getExperience().getCreator();
        if (creador != null) {
            notificationService.notify(creador, NotificationType.PAGO_LIBERADO,
                    "Te liberamos $" + payment.getPayout() + " por "
                            + reservation.getInstance().getExperience().getName(),
                    "/reservations");
        }
        return true;
    }

    private boolean todosEvaluaron(ExperienceInstance instance) {
        List<Reservation> confirmadas =
                reservationRepository.findByInstanceIdAndStatus(instance.getId(), ReservationStatus.CONFIRMADA);
        if (confirmadas.isEmpty()) {
            return false;
        }
        Set<Long> conRating = ratingRepository.findByInstanceId(instance.getId()).stream()
                .map(Rating::getUser)
                .map(u -> u.getId())
                .collect(Collectors.toSet());
        Set<Long> conComentario = commentRepository.findByInstanceId(instance.getId()).stream()
                .map(Comment::getUser)
                .map(u -> u.getId())
                .collect(Collectors.toSet());
        return confirmadas.stream().allMatch(r ->
                conRating.contains(r.getUser().getId()) && conComentario.contains(r.getUser().getId()));
    }

    private boolean ventanaVencida(Payment payment) {
        return payment.getHeldAt() != null
                && !payment.getHeldAt().plusDays(autoReleaseDays).isAfter(LocalDateTime.now());
    }
}
