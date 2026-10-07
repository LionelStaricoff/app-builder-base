package com.openlab.mx.share.shared.exception;

public class NotOwnerException extends DomainException {
    public NotOwnerException() {
        super("No sos el dueño de este recurso");
    }
}
