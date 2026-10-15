package com.openlab.mx.share.identity.client;

/**
 * Usado cuando no hay {@code didit.api-key} configurada: mismo seam que
 * {@code UnconfiguredPaymentGateway}, error claro en vez de NPE.
 */
public class UnconfiguredDiditSessionsClient implements DiditSessionsClient {

    @Override
    public DiditSession createSession(long userId, String workflowId, String callbackUrl) {
        throw new DiditApiException(
                "Didit no está configurado: falta la variable de entorno DIDIT_API_KEY");
    }
}
