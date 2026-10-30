package com.openlab.mx.movo.payment.service;

import java.util.List;

/**
 * Port para obtener las reservas confirmadas de una instancia sin que payment
 * importe Reservation (Corte T8 - instance status).
 */
public interface InstanceConfirmedReservationsPort {
    List<Long> confirmedReservationIds(Long instanceId);
}
