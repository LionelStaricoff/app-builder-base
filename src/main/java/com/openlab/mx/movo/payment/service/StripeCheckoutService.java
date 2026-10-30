package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.notification.entity.NotificationType;
import com.openlab.mx.movo.notification.service.NotificationService;
import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.payment.gateway.GatewayCheckoutRequest;
import com.openlab.mx.movo.payment.gateway.GatewaySession;
import com.openlab.mx.movo.payment.gateway.PaymentGateway;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.reservation.service.ReservationService;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.shared.exception.NotOwnerException;
import com.openlab.mx.movo.user.entity.User;
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
    private final NotificationService notificationService;
    private final ReservationConfirmationPort confirmationPort;
    private final CheckoutReservationPort checkoutReservationPort;
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
                        "experienceId", String.valueOf(instance.getExperience().getId()),
                        "userId", String.valueOf(user.getId()),
                        "attendees", String.valueOf(reservation.getAttendees()),
                        "reservationId", String.valueOf(reservation.getId())));
        GatewaySession session = gateway.createCheckout(request);

        // Único save del pago: ya con la sesión dentro (si el persist falla,
        // el rollback no persigue una fila PENDING sessionless).
        Payment nuevo = Payment.builder()
                .reservationId(reservation.getId())
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
        Long reservationOwnerId = null;
        if (pago.getReservationId() != null) {
            reservationOwnerId = checkoutReservationPort.reservationOwnerId(pago.getReservationId()).orElse(null);
        }
        if (userId == null || reservationOwnerId == null
                || !userId.equals(reservationOwnerId)) {
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
        Payment guardado = paymentRepository.save(pago);
        confirmationPort.confirmarAlPagar(pago.getReservationId());
        return guardado;
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
        if (pago.getReservationId() != null) {
            checkoutReservationPort.cancelPendingReservation(pago.getReservationId());
        }
    }

    /**
     * Resuelve el usuario dueño de un pago por su session id, sin exponer
     * la entidad {@code Reservation} al webhook/controller (preparacion para
     * desacoplar payment de reservation).
     */
    public Long userIdForSession(String sessionId) {
        return paymentRepository.findFirstByStripeSessionId(sessionId)
                .map(p -> {
                    if (p.getReservationId() == null) return null;
                    return checkoutReservationPort.reservationOwnerId(p.getReservationId()).orElse(null);
                })
                .orElse(null);
    }

    /**
     * Marca FAILED un pago iniciado por Stripe PaymentIntent (webhook
     * payment_intent.payment_failed). Devuelve false si no se conoce el piId.
     */
    @Transactional
    public boolean markPaymentFailed(String paymentIntentId) {
        Payment pago = paymentRepository.findFirstByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (pago == null) {
            return false;
        }
        if (pago.getStatus() == PaymentStatus.PENDING
                || pago.getStatus() == PaymentStatus.HELD) {
            pago.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(pago);
        }
        return true;
    }

    private java.util.Optional<Reservation> pendiente(Long instanceId, User user) {
        return reservationRepository.findFirstByInstanceIdAndUserIdAndStatus(
                instanceId, user.getId(), ReservationStatus.PENDIENTE);
    }
}
