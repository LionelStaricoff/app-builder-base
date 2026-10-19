package com.openlab.mx.share.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cupos del checkout: 1..20 (spec §5.3).
 */
public record CheckoutPayRequest(
        @Min(1) @Max(20) int attendees
) {
}
