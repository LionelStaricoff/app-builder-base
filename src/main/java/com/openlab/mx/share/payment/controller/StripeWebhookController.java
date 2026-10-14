package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.entity.StripeEvent;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.payment.repository.StripeEventRepository;
import com.openlab.mx.share.payment.service.StripeCheckoutService;
import com.openlab.mx.share.shared.exception.DomainException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Webhook de Stripe (spec §7): firma obligatoria (400 si inválida),
 * idempotencia por event_id, efecto + registro del evento en la misma
 * transacción (si el efecto falla, el evento no queda como procesado).
 * Endpoint público para Stripe: sin sesión ni CSRF (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeEventRepository stripeEventRepository;
    private final PaymentRepository paymentRepository;
    private final StripeCheckoutService stripeCheckoutService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret = "";

    @PostMapping("/api/stripe/webhooks")
    @Transactional
    public ResponseEntity<String> handle(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.badRequest().body("webhook no configurado");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook con firma inválida (no se persiste)");
            return ResponseEntity.badRequest().body("firma inválida");
        }
        if (stripeEventRepository.existsById(event.getId())) {
            return ResponseEntity.ok("duplicado");
        }

        String objectId;
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject()
                    .getAsJsonObject("data").getAsJsonObject("object");
            objectId = object.get("id").getAsString();
        } catch (RuntimeException e) {
            log.warn("Webhook con payload no parseable: {}", event.getId());
            return ResponseEntity.badRequest().body("payload inválido");
        }
        Boolean cambio = aplicar(event.getType(), objectId);
        if (cambio == null) {
            log.debug("Webhook ignorado (no se persiste): {}", event.getType());
            return ResponseEntity.ok("ignorado");
        }

        stripeEventRepository.save(StripeEvent.builder()
                .eventId(event.getId())
                .type(event.getType())
                .processedAt(LocalDateTime.now())
                .build());
        return ResponseEntity.ok("ok");
    }

    /**
     * @return true si se registró el evento con efecto, false si fue no-op,
     *         null si el tipo no se maneja (no se persiste).
     */
    private Boolean aplicar(String type, String objectId) {
        switch (type) {
            case "checkout.session.completed":
                asegurarCompleto(objectId);
                return true;
            case "checkout.session.expired":
                stripeCheckoutService.expire(objectId);
                return true;
            case "payment_intent.payment_failed":
                return marcarFailed(objectId);
            default:
                return null;
        }
    }

    private void asegurarCompleto(String sessionId) {
        try {
            stripeCheckoutService.complete(sessionId, stripeUser(sessionId));
        } catch (DomainException e) {
            log.warn("checkout.session.completed sobre session_id conocido pero no en DB: {}", e.getMessage());
            throw e;
        }
    }

    private Long stripeUser(String sessionId) {
        return paymentRepository.findFirstByStripeSessionId(sessionId)
                .map(p -> p.getReservation().getUser().getId())
                .orElseThrow(() -> new DomainException("Sesión de pago no encontrada"));
    }

    private Boolean marcarFailed(String paymentIntentId) {
        Payment pago = paymentRepository.findFirstByStripePaymentIntentId(paymentIntentId)
                .orElse(null);
        if (pago == null) {
            return false; // pi no conocido: no-op idempotente
        }
        if (pago.getStatus() == PaymentStatus.PENDING
                || pago.getStatus() == PaymentStatus.HELD) {
            pago.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(pago);
        }
        return true;
    }
}
