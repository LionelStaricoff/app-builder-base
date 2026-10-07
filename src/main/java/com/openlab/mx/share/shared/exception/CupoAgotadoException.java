package com.openlab.mx.share.shared.exception;

/** No hay cupos disponibles para la cantidad pedida (spec §8). */
public class CupoAgotadoException extends DomainException {
    public CupoAgotadoException(String message) {
        super(message);
    }
}
