package com.openlab.mx.share.shared.exception;

/**
 * El país pedido para la cuenta de cobro (Stripe Connect) no está en la
 * lista blanca de países donde Stripe permite crear cuentas.
 *
 * Ejemplo real: Colombia no es un país soportado por Stripe
 * (https://stripe.com/global) — intentar crear la cuenta con country=CO
 * devuelve {@code country_unsupported} desde la API de Stripe.
 */
public class UnsupportedPayoutCountryException extends RuntimeException {

    public UnsupportedPayoutCountryException(String message) {
        super(message);
    }
}
