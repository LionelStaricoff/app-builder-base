package com.openlab.mx.share.identity.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openlab.mx.share.identity.client.DiditWebhookSigner;
import com.openlab.mx.share.identity.dto.WebhookDelivery;
import com.openlab.mx.share.identity.service.DiditWebhookService;
import com.openlab.mx.share.identity.service.InvalidWebhookPayloadException;
import com.openlab.mx.share.identity.service.WebhookOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de Didit (POST /api/webhooks/didit). Firma SIEMPRE obligatoria:
 * 401 si la firma o el timestamp no pasan la verificación (ventana 300s),
 * 400 si el payload es ilegible o no trae event_id. El servicio se encarga
 * de idempotencia y decisión. Endpoint público para Didit (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DiditWebhookController {

    private final DiditWebhookSigner signer;
    private final DiditWebhookService webhookService;

    @PostMapping("/api/webhooks/didit")
    public ResponseEntity<String> handle(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature-V2", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp) {

        if (!signer.verify(rawBody, signature, timestamp)) {
            log.warn("Webhook de Didit rechazado: firma o timestamp inválido");
            return ResponseEntity.status(401).body("firma inválida");
        }

        WebhookDelivery delivery;
        try {
            delivery = parse(rawBody);
        } catch (RuntimeException e) {
            log.warn("Webhook de Didit con payload ilegible: {}", e.getMessage());
            return ResponseEntity.badRequest().body("payload inválido");
        }

        try {
            WebhookOutcome outcome = webhookService.handle(delivery);
            return ResponseEntity.ok(outcome.name().toLowerCase());
        } catch (InvalidWebhookPayloadException e) {
            log.warn("Webhook de Didit inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private WebhookDelivery parse(String rawBody) {
        JsonObject json = JsonParser.parseString(rawBody).getAsJsonObject();
        return new WebhookDelivery(
                text(json, "event_id"),
                text(json, "webhook_type"),
                text(json, "session_id"),
                text(json, "vendor_data"),
                text(json, "status"));
    }

    private String text(JsonObject json, String field) {
        JsonElement element = json.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
