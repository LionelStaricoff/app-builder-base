package com.openlab.mx.share.shared.exception;

/** Operación inválida sobre un pago en escrow, p.ej. liberar uno ya reembolsado (spec §8). */
public class EscrowException extends DomainException {
    public EscrowException(String message) {
        super(message);
    }
}
