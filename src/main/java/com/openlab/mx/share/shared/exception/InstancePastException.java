package com.openlab.mx.share.shared.exception;

/** No se puede reservar una instancia cuya fecha ya pasó (spec §8). */
public class InstancePastException extends DomainException {
    public InstancePastException(String message) {
        super(message);
    }
}
