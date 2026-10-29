package com.openlab.mx.movo.identity.dto;

/**
 * Payload mínimo de un webhook de Didit ya parseado por el controller.
 * {@code vendorData} llega como String crudo y se parsea defensivo en el
 * servicio (es el puente userId ↔ session).
 */
public record WebhookDelivery(
        String eventId,
        String type,
        String sessionId,
        String vendorData,
        String diditStatus) {
}
