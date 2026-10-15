package com.openlab.mx.share.shared.exception;

/**
 * Lanzada por el gate de identidad: el usuario debe completar la
 * verificación con Didit antes de la acción protegida. El
 * GlobalExceptionHandler la convierte en redirect a /verificacion.
 */
public class UnverifiedIdentityException extends DomainException {

    public UnverifiedIdentityException() {
        super("Tenés que verificar tu identidad para continuar");
    }
}
