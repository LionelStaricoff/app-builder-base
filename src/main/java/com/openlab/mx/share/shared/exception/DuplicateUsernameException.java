package com.openlab.mx.share.shared.exception;

public class DuplicateUsernameException extends DomainException {
    public DuplicateUsernameException() {
        super("El nombre de usuario ya está en uso");
    }
}
