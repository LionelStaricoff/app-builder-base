package com.openlab.mx.movo.payment.service;

import java.util.Optional;

/**
 * Port de consulta/estado de reserva para el checkout de pago sin que payment
 * importe Reservation directamente (Corte D3 T8).
 */
public interface CheckoutReservationPort {
    /** Id del dueño de la reserva, o vacío. */
    Optional<Long> reservationOwnerId(Long reservationId);

    /** Cancela la reserva si está PENDIENTE; idempotente. */
    boolean cancelPendingReservation(Long reservationId);
}
