package com.openlab.mx.share.reservation.service;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.service.NotificationService;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Los 3 casos de cancelación (spec §5.4):
 * 1. participante cancela → cupo libre ya, reembolso 100% (>24h) o parcial (≤24h).
 * 2. creador cancela la fecha → refund 100% a todos, notificación masiva, chat archivado.
 * 3. cupo mínimo → mismo efecto que el caso 2.
 */
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final NotificationService notificationService;
    private final com.openlab.mx.share.payment.gateway.PaymentGateway gateway;

    @Value("${partial-refund-rate}")
    private BigDecimal partialRefundRate = new BigDecimal("0.5");

    /** Caso 1. */
    @Transactional
    public Reservation cancelByParticipant(Long reservationId, User u) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new DomainException("Reserva no encontrada"));
        if (u == null || reservation.getUser() == null
                || !reservation.getUser().getId().equals(u.getId())) {
            throw new NotOwnerException();
        }
        if (reservation.getStatus() == ReservationStatus.CANCELADO) {
            return reservation;
        }

        boolean reembolsoTotal = masDe24Horas(reservation.getInstance());
        reservation.setStatus(ReservationStatus.CANCELADO);
        reservationRepository.save(reservation);
        reembolsar(reservation, reembolsoTotal);

        User creador = reservation.getInstance().getExperience().getCreator();
        if (creador != null) {
            notificationService.notify(creador, NotificationType.CANCELACION_REEMBOLSO,
                    "Un participante canceló su reserva en "
                            + reservation.getInstance().getExperience().getName(),
                    "/reservations");
        }
        return reservation;
    }

    /** Caso 2: solo el dueño de la experiencia. */
    @Transactional
    public void cancelByCreator(Long instanceId, User creator) {
        ExperienceInstance instance = instance(instanceId);
        User dueno = instance.getExperience().getCreator();
        if (creator == null || dueno == null || !dueno.getId().equals(creator.getId())) {
            throw new NotOwnerException();
        }
        cancelarFecha(instance);
    }

    /** Caso 3: lo invoca el job (o el botón del dueño, validado en el controller). */
    @Transactional
    public void cancelByMinCapacity(Long instanceId) {
        cancelarFecha(instance(instanceId));
    }

    private void cancelarFecha(ExperienceInstance instance) {
        if (instance.getStatus() == InstanceStatus.CANCELLED) {
            return;
        }
        instance.setStatus(InstanceStatus.CANCELLED);
        instance.setArchived(true);
        instanceRepository.save(instance);

        List<Reservation> reservas =
                reservationRepository.findByInstanceIdAndStatusNot(instance.getId(), ReservationStatus.CANCELADO);
        List<User> inscritos = new ArrayList<>();
        for (Reservation reservation : reservas) {
            reservation.setStatus(ReservationStatus.CANCELADO);
            reservationRepository.save(reservation);
            reembolsar(reservation, true);
            User participante = reservation.getUser();
            if (participante != null && inscritos.stream()
                    .noneMatch(x -> x.getId().equals(participante.getId()))) {
                inscritos.add(participante);
            }
        }
        notificationService.notifyMassive(inscritos, NotificationType.CANCELACION_REEMBOLSO,
                "La fecha de " + instance.getExperience().getName() + " fue cancelada", "/feed");
    }

    private void reembolsar(Reservation reservation, boolean total) {
        paymentRepository.findByReservationId(reservation.getId()).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.HELD) {
                return;
            }
            if (total) {
                payment.setStatus(PaymentStatus.REFUNDED_FULL);
                payment.setRefund(payment.getAmount());
            } else {
                payment.setStatus(PaymentStatus.REFUNDED_PARTIAL);
                payment.setRefund(payment.getAmount().multiply(partialRefundRate)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
            paymentRepository.save(payment);
            // Spec §4: el reembolso aplica dinero real solo con pi conocido
            // (pagos Stripe); legacy sin referencia Stripe queda contable.
            if (payment.getStripePaymentIntentId() != null) {
                if (total) {
                    gateway.cancel(payment.getStripePaymentIntentId());
                } else {
                    gateway.capturePartial(payment.getStripePaymentIntentId(), payment.getRefund());
                }
            }
        });
    }

    /** >24h para el inicio → reembolso 100%; ≤24h → parcial. */
    private boolean masDe24Horas(ExperienceInstance instance) {
        LocalDateTime inicio = LocalDateTime.of(instance.getDate(), instance.getStartTime());
        return LocalDateTime.now().isBefore(inicio.minusHours(24));
    }

    private ExperienceInstance instance(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
    }
}
