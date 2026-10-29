package com.openlab.mx.movo.identity.service;

/** Resultado del manejo de un webhook, para responder 200 con cuerpo informativo. */
public enum WebhookOutcome {

    /** Se aplicó la decisión a la fila del usuario. */
    PROCESSED,

    /** event_id ya procesado: no-op (idempotencia). */
    DUPLICATE,

    /** Evento sin fila correspondiente o con literal de estado desconocido. */
    IGNORED
}
