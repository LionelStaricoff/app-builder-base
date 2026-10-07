package com.openlab.mx.share.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Checkout simulado: solo se valida el formato, nunca el contenido
 * (spec §5.3 — pago simulado).
 */
public record CheckoutRequest(
        @NotBlank @Pattern(regexp = "\\d{12,19}") String cardNumber,
        @NotBlank String holder,
        String expiry,
        String cvv) {
}
