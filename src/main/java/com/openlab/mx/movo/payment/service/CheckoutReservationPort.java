package com.openlab.mx.movo.payment.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port de consulta/estado de reserva para el checkout de pago sin que payment
 * importe Reservation directamente (Cortes D3/D4 T8).
 */
public interface CheckoutReservationPort {
    /** Id del due��o de la reserva, o vac��o. */
    Optional<Long> reservationOwnerId(Long reservationId);

    /** Cancela la reserva si estǭ PENDIENTE; idempotente. */
    boolean cancelPendingReservation(Long reservationId);

    /**
     * Reusa una reserva PENDIENTE existente para el mismo usuario/instancia
     * (idempotencia, spec §11.2) o vacío.
     */
    Optional<ReservationCheckoutData> findPendingReservation(Long instanceId, Long userId);

    /** Crea una reserva PENDIENTE (validando cupo, KYC, etc.) y devuelve sus datos. */
    ReservationCheckoutData reserveReservation(Long instanceId, int attendees, Long userId);

    /**
     * Datos de una reserva para armar el checkout de pago, sin exponer
     * entidades Reservation/ExperienceInstance al payment.
     */
    record ReservationCheckoutData(
            Long reservationId,
            int attendees,
            Long experienceId,
            BigDecimal price,
            String currency,
            String name) {
    }
}
