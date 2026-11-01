package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.mobility.ride.entity.Ride;
import com.openlab.mx.movo.mobility.ride.entity.RideOffer;
import com.openlab.mx.movo.mobility.ride.entity.RideOfferStatus;
import com.openlab.mx.movo.mobility.ride.repository.RideOfferRepository;
import com.openlab.mx.movo.mobility.ride.service.RideService;
import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.entity.PayoutStatus;
import com.openlab.mx.movo.payment.gateway.GatewayCheckoutRequest;
import com.openlab.mx.movo.payment.gateway.GatewaySession;
import com.openlab.mx.movo.payment.gateway.PaymentGateway;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RidePaymentService {

    private final RideService rideService;
    private final RideOfferRepository offers;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final CurrentUser currentUser;

    @Value("${escrow.commission-rate:0.05}")
    private BigDecimal commissionRate;

    private static final int MONEY_SCALE = 2;

    @Transactional
    public Map<String, String> createCheckout(Long rideId) {
        Ride ride = rideService.get(rideId);
        Long riderId = currentUser.require().getId();
        if (!riderId.equals(ride.getRiderId())) {
            throw new DomainException("Solo el rider puede pagar el viaje");
        }
        if (payments.findByRideId(rideId).isPresent()) {
            throw new DomainException("El viaje ya tiene un pago asociado");
        }
        RideOffer accepted = offers.findByRideIdAndStatus(rideId, RideOfferStatus.ACCEPTED)
                .orElseThrow(() -> new DomainException("No hay oferta aceptada para el viaje"));

        BigDecimal amount = accepted.getAmount();
        BigDecimal commission = amount.multiply(commissionRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal payout = amount.subtract(commission).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        String currency = accepted.getCurrencyCode();

        GatewaySession session = gateway.createCheckout(new GatewayCheckoutRequest(
                amount, currency, 1, "Viaje MovO", Map.of("rideId", String.valueOf(rideId))));

        Payment payment = Payment.builder()
                .rideId(rideId)
                .creatorId(accepted.getDriverId())
                .currencyCode(currency)
                .amount(amount)
                .commission(commission)
                .status(PaymentStatus.PENDING)
                .payout(payout)
                .payoutStatus(null)
                .stripeSessionId(session.sessionId())
                .build();
        payments.save(payment);

        return Map.of("sessionId", session.sessionId(), "url", session.url());
    }

    @Transactional
    public void markPaid(String sessionId) {
        Payment payment = payments.findFirstByStripeSessionId(sessionId)
                .orElseThrow(() -> new DomainException("Pago no encontrado para la sesión"));
        if (payment.getStatus() == PaymentStatus.HELD) {
            return;
        }
        GatewaySession info = gateway.authorizeInfo(sessionId);
        if (!"paid".equals(info.status())) {
            throw new DomainException("La sesión aún no está pagada");
        }
        payment.setStatus(PaymentStatus.HELD);
        payment.setStripePaymentIntentId(info.paymentIntentId());
        payment.setHeldAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
        payment.setPayoutStatus(com.openlab.mx.movo.payment.entity.PayoutStatus.PENDING);
        payments.save(payment);
    }

    @Transactional
    public void captureOnFinish(Long rideId) {
        Payment payment = payments.findByRideId(rideId)
                .orElseThrow(() -> new DomainException("No hay pago para el viaje"));
        if (payment.getStatus() != PaymentStatus.HELD) {
            return;
        }
        if (payment.getStripePaymentIntentId() != null) {
            gateway.capture(payment.getStripePaymentIntentId());
        }
        payment.setStatus(PaymentStatus.RELEASED);
        payments.save(payment);
    }

    @Transactional
    public void cancel(Long rideId) {
        Payment payment = payments.findByRideId(rideId).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getStripePaymentIntentId() != null && payment.getStatus() == PaymentStatus.HELD) {
            gateway.cancel(payment.getStripePaymentIntentId());
        }
        payment.setStatus(PaymentStatus.REFUNDED_FULL);
        payments.save(payment);
    }
}
