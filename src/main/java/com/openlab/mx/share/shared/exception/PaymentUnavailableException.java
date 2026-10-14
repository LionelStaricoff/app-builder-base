package com.openlab.mx.share.shared.exception;

/**
 * Sin keys de Stripe configuradas: ningún método del pasarela está disponible
 * (spec §8: mensaje claro, nunca NullPointerException).
 */
public class PaymentUnavailableException extends DomainException {

    public PaymentUnavailableException() {
        super("El pago no está disponible ahora: Stripe no está configurado");
    }
}
