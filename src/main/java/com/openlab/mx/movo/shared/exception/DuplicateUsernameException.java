package com.openlab.mx.movo.shared.exception;

public class DuplicateUsernameException extends DomainException {
    public DuplicateUsernameException() {
        super("El nombre de usuario ya está en uso");
    }
}
