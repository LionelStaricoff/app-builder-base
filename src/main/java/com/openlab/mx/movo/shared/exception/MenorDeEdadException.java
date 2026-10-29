package com.openlab.mx.movo.shared.exception;

public class MenorDeEdadException extends DomainException {
    public MenorDeEdadException() {
        super("Debes tener al menos 18 años para registrarte");
    }
}
