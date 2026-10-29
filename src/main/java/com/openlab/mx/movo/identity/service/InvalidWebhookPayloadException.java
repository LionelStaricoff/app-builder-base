package com.openlab.mx.movo.identity.service;

/** Payload de webhook inválido (ej: sin event_id). El controller responde 400. */
public class InvalidWebhookPayloadException extends RuntimeException {

    public InvalidWebhookPayloadException(String message) {
        super(message);
    }
}
