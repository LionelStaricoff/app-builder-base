package com.openlab.mx.movo.payment.service;

import java.util.Optional;

/**
 * Port para consultar estado de una reserva (confirmada + instancia) sin que
 * payment importe Reservation/Experience.
 */
public interface ReservationStatusPort {
    /** InstanceId si la reserva está CONFIRMADA; vacío en otro caso. */
    Optional<Long> confirmedInstanceId(Long reservationId);
}
