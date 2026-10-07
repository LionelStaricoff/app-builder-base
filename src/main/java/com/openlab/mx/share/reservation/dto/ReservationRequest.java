package com.openlab.mx.share.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Cupos pedidos en el picker de reservas. */
public record ReservationRequest(
        @Min(1) @Max(20) int attendees) {
}
