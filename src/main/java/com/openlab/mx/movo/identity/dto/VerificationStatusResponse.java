package com.openlab.mx.movo.identity.dto;

import java.time.LocalDateTime;

/**
 * Vista del estado de verificación. {@code status} es el literal propio
 * ({@code NOT_STARTED} cuando no hay fila) y {@code verifiedAt} solo se
 * setea cuando es VERIFIED.
 */
public record VerificationStatusResponse(String status, LocalDateTime verifiedAt) {
}
