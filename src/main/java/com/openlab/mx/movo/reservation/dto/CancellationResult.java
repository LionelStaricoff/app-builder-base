package com.openlab.mx.movo.reservation.dto;

import com.openlab.mx.movo.reservation.entity.Reservation;

import java.math.BigDecimal;

/**
 * Resultado de cancelar una reserva del participante: la reserva cancelada y
 * la proporción del monto que se le devuelve (la que efectivamente se aplicó,
 * para poder mostrarla en la confirmación).
 */
public record CancellationResult(Reservation reservation, BigDecimal refundRate) {
}
