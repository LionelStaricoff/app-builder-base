package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.payment.gateway.GatewayCheckoutRequest;
import com.openlab.mx.share.payment.gateway.GatewaySession;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.ReservationService;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Orquestación Stripe Checkout (spec §5-6):
 * - start: reserva PENDIENTE (idempotente) + payment PENDING + Checkout Session.
 * - complete: revalida la sesión en Stripe (nunca el query param) → PENDING→HELD.
 * - expire: checkout.session.expired → FAILED y libera el cupo.
 * DB es source of truth del estado; el gateway aplica el efecto del dinero.
 */
@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;

    @Value("${escrow.commission-rate}")
    private BigDecimal commissionRate = new BigDecimal("0.05");

    /**
     * Doble submit: si ya hay reserva PENDIENTE + payment PENDING con sesión,
     * reusa esa sesión (redirect a su URL) sin duplicar nada (spec §11.2).
     */
    @Transactional
    public GatewaySession start(Long instanceId, int attendees, User user) {
        Reservation reservation = pendiente(instanceId, user).orElse(null);
        if (reservation == null) {
            reservation = reservationService.reserve(instanceId, attendees, user);
        }
        Payment pago = paymentRepository.findByReservationId(reservation.getId()).orElse(null);
        if (pago != null && pago.getStatus() == PaymentStatus.PENDING
                && pago.getStripeSessionId() != null) {
            return gateway.authorizeInfo(pago.getStripeSessionId());
        }

        ExperienceInstance instance = reservation.getInstance();
        BigDecimal price = instance.getExperience().getPrice();
        BigDecimal amount = price.multiply(BigDecimal.valueOf(reservation.getAttendees()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = amount.multiply(commissionRate)
                .setScale(2, RoundingMode.HALF_UP);

        String currency = instance.getExperience().getCurrencyCode() == null
                ? "MXN" : instance.getExperience().getCurrencyCode();
        GatewayCheckoutRequest request = new GatewayCheckoutRequest(
                price, currency, reservation.getAttendees(), instance.getExperience().getName(),
                Map.of("instanceId", String.valueOf(instanceId),
                        "userId", String.valueOf(user.getId()),
                        "attendees", String.valueOf(reservation.getAttendees()),
                        "reservationId", String.valueOf(reservation.getId())));
        GatewaySession session = gateway.createCheckout(request);

        // Único save del pago: ya con la sesión dentro (si el persist falla,
        // el rollback no persigue una fila PENDING sessionless).
        Payment nuevo = Payment.builder()
                .reservation(reservation)
                .amount(amount)
                .commission(commission)
                .status(PaymentStatus.PENDING)
                .stripeSessionId(session.sessionId())
                .stripePaymentIntentId(session.paymentIntentId())
                .build();
        paymentRepository.save(nuevo);
        return session;
    }

    /**
     * Retorno de Stripe (o webhook): revalida contra la puerta real
     * (authorizeInfo), valida el dueño y PENDING→HELD: guarda
     * stripePaymentIntentId + heldAt (spec §3.2). Idempotente: ya HELD → no-op.
     */
    @Transactional
    public Payment complete(String sessionId, Long userId) {
        Payment pago = paymentRepository.findFirstByStripeSessionId(sessionId)
                .orElseThrow(() -> new DomainException("Sesión de pago no encontrada"));
        if (pago.getStatus() != PaymentStatus.PENDING) {
            return pago; // HELD y después: no-op idempotente
        }
        if (userId == null || pago.getReservation() == null
                || pago.getReservation().getUser() == null
                || !userId.equals(pago.getReservation().getUser().getId())) {
            throw new NotOwnerException();
        }
        String estado;
        String paymentIntentId;
        try {
            GatewaySession real = gateway.authorizeInfo(sessionId);
            estado = real.status();
            paymentIntentId = real.paymentIntentId();
        } catch (NoSuchElementException _) {
            throw new DomainException("Sesión de pago no encontrada");
        }
        if (!"paid".equals(estado)) {
            throw new DomainException("El pago no está completado");
        }
        pago.setStatus(PaymentStatus.HELD);
        pago.setStripePaymentIntentId(paymentIntentId);
        pago.setHeldAt(LocalDateTime.now(ZoneId.systemDefault()));
        return paymentRepository.save(pago);
    }

    /**
     * checkout.session.expired (spec §4): PENDING→FAILED y reserva CANCELADO
     * para liberar los cupos consumidos por el checkout abandonado.
     */
    @Transactional
    public void expire(String sessionId) {
        Payment pago = paymentRepository.findFirstByStripeSessionId(sessionId).orElse(null);
        if (pago == null || pago.getStatus() != PaymentStatus.PENDING) {
            return; // HELD/RELEASED/FAILED: no-op
        }
        pago.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(pago);
        Reservation reservation = pago.getReservation();
        if (reservation != null
                && reservation.getStatus() == ReservationStatus.PENDIENTE) {
            reservation.setStatus(ReservationStatus.CANCELADO);
            reservationRepository.save(reservation);
        }
    }

    private java.util.Optional<Reservation> pendiente(Long instanceId, User user) {
        return reservationRepository.findFirstByInstanceIdAndUserIdAndStatus(
                instanceId, user.getId(), ReservationStatus.PENDIENTE);
    }
}
