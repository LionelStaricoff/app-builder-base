package com.openlab.mx.movo.payment.controller;

import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.entity.PayoutStatus;
import com.openlab.mx.movo.payment.entity.StripeEvent;
import com.openlab.mx.movo.payment.repository.ConnectAccountRepository;
import com.openlab.mx.movo.payment.repository.ConnectPayoutRepository;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.payment.repository.StripeEventRepository;
import com.openlab.mx.movo.payment.service.ConnectAccountService;
import com.openlab.mx.movo.payment.service.StripeCheckoutService;
import com.openlab.mx.movo.shared.exception.DomainException;
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

import java.time.ZoneId;import java.time.LocalDateTime;

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
    private final ConnectAccountRepository connectAccountRepository;
    private final ConnectPayoutRepository connectPayoutRepository;
    private final ConnectAccountService connectAccountService;

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
        } catch (SignatureVerificationException _) {
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
        } catch (RuntimeException _) {
            log.warn("Webhook con payload no parseable: {}", event.getId());
            return ResponseEntity.badRequest().body("payload inválido");
        }
        Resultado resultado = aplicar(event.getType(), objectId);
        if (resultado == Resultado.IGNORADO) {
            log.debug("Webhook ignorado (no se persiste): {}", event.getType());
            return ResponseEntity.ok("ignorado");
        }

        stripeEventRepository.save(StripeEvent.builder()
                .eventId(event.getId())
                .type(event.getType())
                .processedAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build());
        return ResponseEntity.ok("ok");
    }

    /**
     * Qué se hizo con un evento del webhook.
     *
     * <p>Antes esto se expresaba con un {@code Boolean} que podía ser null
     * (tipo no manejado). Un enum deja los tres estados explícitos y evita
     * el null flotante.
     */
    private enum Resultado {
        /** El evento tuvo efecto: se persiste. */
        APLICADO,
        /** Se procesó pero no cambió nada (no-op idempotente): se persiste. */
        SIN_EFFECTO,
        /** Tipo de evento no soportado: no se persiste. */
        IGNORADO
    }

    /**
     * @return qué se hizo con el evento (ver {@link Resultado}).
     */
    private Resultado aplicar(String type, String objectId) {
        switch (type) {
            case "checkout.session.completed":
                asegurarCompleto(objectId);
                return Resultado.APLICADO;
            case "checkout.session.expired":
                stripeCheckoutService.expire(objectId);
                return Resultado.APLICADO;
            case "payment_intent.payment_failed":
                return marcarFailed(objectId) ? Resultado.APLICADO : Resultado.SIN_EFFECTO;
            // ─── Stripe Connect ─────────────────────────────────────────────
            case "account.updated":
                return actualizarCuentaConnect(objectId) ? Resultado.APLICADO : Resultado.SIN_EFFECTO;
            case "transfer.created":
                return marcarTransferCompletado(objectId) ? Resultado.APLICADO : Resultado.SIN_EFFECTO;
            case "transfer.reversed":
                return marcarTransferFallido(objectId) ? Resultado.APLICADO : Resultado.SIN_EFFECTO;
            default:
                return Resultado.IGNORADO;
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

    // ─── Stripe Connect webhooks ─────────────────────────────────────────

    /** account.updated: sincroniza el estado de la cuenta Connect. */
    private boolean actualizarCuentaConnect(String stripeAccountId) {
        var account = connectAccountRepository.findByStripeAccountId(stripeAccountId)
                .orElse(null);
        if (account == null) {
            return false; // cuenta no conocida: no-op
        }
        // Re-sincronizar desde Stripe
        connectAccountService.syncAccountStatus(account.getUser());
        return true;
    }

    /** transfer.paid: marca el payout como completado. */
    private boolean marcarTransferCompletado(String transferId) {
        var payout = connectPayoutRepository.findAll().stream()
                .filter(p -> transferId.equals(p.getStripeTransferId()))
                .findFirst()
                .orElse(null);
        if (payout == null) {
            return false;
        }
        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setCompletedAt(java.time.LocalDateTime.now(ZoneId.systemDefault()));
        connectPayoutRepository.save(payout);
        return true;
    }

    /** transfer.failed: marca el payout como fallido. */
    private boolean marcarTransferFallido(String transferId) {
        var payout = connectPayoutRepository.findAll().stream()
                .filter(p -> transferId.equals(p.getStripeTransferId()))
                .findFirst()
                .orElse(null);
        if (payout == null) {
            return false;
        }
        payout.setStatus(PayoutStatus.FAILED);
        payout.setFailureReason("Stripe transfer failed");
        connectPayoutRepository.save(payout);
        return true;
    }

    private Long stripeUser(String sessionId) {
        return paymentRepository.findFirstByStripeSessionId(sessionId)
                .map(p -> p.getReservation().getUser().getId())
                .orElseThrow(() -> new DomainException("Sesión de pago no encontrada"));
    }

    private boolean marcarFailed(String paymentIntentId) {
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
