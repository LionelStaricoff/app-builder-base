package com.openlab.mx.movo.payment.service;

import java.util.Optional;

/**
 * Port para resolver la moneda de payout de un pago sin que payment
 * importe Reservation/Experience (Corte T8 - payout currency).
 * 
 * 
 */
public interface PayoutCurrencyPort {
    /** Moneda del payout asociado a la reserva, o vacío si no existe. */
    Optional<String> currencyOfReservation(Long reservationId);
}
