package com.openlab.mx.movo.shared.exception;

public class NotOwnerException extends DomainException {
    public NotOwnerException() {
        super("No sos el dueño de este recurso");
    }
}
