package com.openlab.mx.share.favorite.dto;

import java.time.LocalDate;

/** Experiencia completada por el usuario con su evaluación (spec §5.2). */
public record LivedExperienceResponse(
        Long experienceId,
        String experienceName,
        LocalDate date,
        int stars,
        String comment) {
}
