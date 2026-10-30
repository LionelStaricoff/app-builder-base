package com.openlab.mx.movo.payment.service;

/**
 * Port para el paso de pago completo -> reserva CONFIRMADA (instant book).
 * Evita que payment dependa de Reservation/Experience directamente; la
 * feature reservation implementa este port (Corte post-C).
 */
public interface ReservationConfirmationPort {
    void confirmarAlPagar(Long reservationId);
}
