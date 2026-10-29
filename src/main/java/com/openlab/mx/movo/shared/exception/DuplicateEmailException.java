package com.openlab.mx.movo.shared.exception;

public class DuplicateEmailException extends DomainException {
    public DuplicateEmailException() {
        super("El email ya está registrado");
    }
}