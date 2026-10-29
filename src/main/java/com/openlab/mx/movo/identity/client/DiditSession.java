package com.openlab.mx.movo.identity.client;

/** Sesión hosteada de Didit: id + URL donde el usuario completa el flujo. */
public record DiditSession(String sessionId, String url) {
}
