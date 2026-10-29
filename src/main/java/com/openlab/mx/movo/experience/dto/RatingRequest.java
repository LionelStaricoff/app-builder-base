package com.openlab.mx.movo.experience.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Evaluación al terminar una experiencia (spec §5.3): estrellas 1..5 + comentario.
 */
public record RatingRequest(
        @Min(1) @Max(5) int stars,
        @Size(max = 500) String text
) {
}
