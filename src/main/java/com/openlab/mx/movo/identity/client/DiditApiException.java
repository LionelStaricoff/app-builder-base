package com.openlab.mx.movo.identity.client;

/** Error al hablar con la API de Didit (403/429/5xx o respuesta ilegible). */
public class DiditApiException extends RuntimeException {

    public DiditApiException(String message) {
        super(message);
    }

    public DiditApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
