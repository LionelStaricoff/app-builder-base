package com.openlab.mx.movo.shared.exception;

/** VERIFIED es terminal: no se reinicia la verificación de identidad. */
public class VerificationAlreadyCompletedException extends DomainException {

    public VerificationAlreadyCompletedException() {
        super("Tu identidad ya está verificada");
    }
}
