package com.openlab.mx.share.experience.dto;

import java.time.LocalDateTime;

/**
 * Una pregunta del detalle con su respuesta (nullable) y quién la hizo.
 *
 * <p>Se arma en el servicio, no en la vista: el template no debe tocar el
 * proxy lazy de {@code question.user}.
 */
public record ExperienceQuestionDTO(
        Long id,
        String askerName,
        String askerAvatar,
        String body,
        LocalDateTime createdAt,
        String answer,
        LocalDateTime answeredAt
) {

    /** {@code true} cuando la anfitriona ya respondió. */
    public boolean isAnswered() {
        return answer != null && !answer.isBlank();
    }
}
