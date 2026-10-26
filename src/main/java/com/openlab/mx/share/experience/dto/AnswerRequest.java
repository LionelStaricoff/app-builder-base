package com.openlab.mx.share.experience.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Respuesta de la anfitriona a una pregunta publica del detalle. */
public record AnswerRequest(
        @NotBlank @Size(min = 1, max = 500) String answer
) {
}
