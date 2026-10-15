package com.openlab.mx.share.identity.client;

/**
 * Puerto de creación de sesiones de Didit. Cambiar de proveedor KYC
 * significa implementar esta interfaz; los use cases no cambian.
 */
public interface DiditSessionsClient {

    /**
     * Crea una sesión hosteada en Didit para el usuario dado.
     *
     * @param userId       id interno del usuario; viaja como vendor_data (puente del webhook).
     * @param workflowId   workflow configurado en Didit Business.
     * @param callbackUrl  URL pública a la que Didit devuelve al usuario al terminar.
     */
    DiditSession createSession(long userId, String workflowId, String callbackUrl);
}
