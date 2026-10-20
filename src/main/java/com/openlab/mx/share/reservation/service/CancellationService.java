package com.openlab.mx.share.reservation.service;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.service.NotificationService;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.dto.CancellationResult;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Los 3 casos de cancelación (spec §5.4):
 * 1. participante cancela → cupo libre ya, reembolso por anticipación
 *    (según {@link RefundPolicy}: 90% con más de 24h, 50% con 24h o menos).
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
    private final RefundPolicy refundPolicy;

    /** Caso 1. */
    @Transactional
    public CancellationResult cancelByParticipant(Long reservationId, User u) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new DomainException("Reserva no encontrada"));
        if (u == null || reservation.getUser() == null
                || !reservation.getUser().getId().equals(u.getId())) {
            throw new NotOwnerException();
        }
        BigDecimal tasa = refundPolicy.rateFor(reservation.getInstance());
        if (reservation.getStatus() == ReservationStatus.CANCELADO) {
            return new CancellationResult(reservation, tasa);
        }

        reservation.setStatus(ReservationStatus.CANCELADO);
        reservationRepository.save(reservation);
        reembolsar(reservation, tasa);

        User creador = reservation.getInstance().getExperience().getCreator();
        if (creador != null) {
            notificationService.notify(creador, NotificationType.CANCELACION_REEMBOLSO,
                    "Un participante canceló su reserva en "
                            + reservation.getInstance().getExperience().getName(),
                    "/reservations");
        }
        return new CancellationResult(reservation, tasa);
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
            reembolsar(reservation, BigDecimal.ONE);
            User participante = reservation.getUser();
            if (participante != null && inscritos.stream()
                    .noneMatch(x -> x.getId().equals(participante.getId()))) {
                inscritos.add(participante);
            }
        }
        notificationService.notifyMassive(inscritos, NotificationType.CANCELACION_REEMBOLSO,
                "La fecha de " + instance.getExperience().getName() + " fue cancelada", "/feed");
    }

    /**
     * Aplica el reembolso. {@code tasa} es lo que se devuelve al participante;
     * el resto queda retenido por la plataforma (spec §4). 100% → cancel de la
     * autorización; parcial → capture parcial por lo retenido.
     */
    private void reembolsar(Reservation reservation, BigDecimal tasa) {
        paymentRepository.findByReservationId(reservation.getId()).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.HELD) {
                return;
            }
            BigDecimal monto = payment.getAmount();
            BigDecimal devuelto = monto.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal retenido = monto.subtract(devuelto);
            boolean total = devuelto.compareTo(monto) >= 0;
            payment.setStatus(total ? PaymentStatus.REFUNDED_FULL : PaymentStatus.REFUNDED_PARTIAL);
            payment.setRefund(devuelto);
            paymentRepository.save(payment);
            // Spec §4: el reembolso aplica dinero real solo con pi conocido
            // (pagos Stripe); legacy sin referencia Stripe queda contable.
            if (payment.getStripePaymentIntentId() != null) {
                if (total) {
                    gateway.cancel(payment.getStripePaymentIntentId());
                } else {
                    gateway.capturePartial(payment.getStripePaymentIntentId(), retenido);
                }
            }
        });
    }

    private ExperienceInstance instance(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
    }
}
