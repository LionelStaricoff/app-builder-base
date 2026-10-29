package com.openlab.mx.movo.experience.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pregunta publica que una persona interesada le hace a la anfitriona.
 *
 * <p>El minimo existe para que no se llene la seccion con "ok" o "?".
 */
public record QuestionRequest(
        @NotBlank @Size(min = 3, max = 500) String body
) {
}
