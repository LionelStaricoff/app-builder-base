package com.openlab.mx.share.experience.dto;

import java.time.LocalDate;

/**
 * Una reseña del detalle: usuario, estrellas y comentario (nullable cuando
 * el participante solo puso estrellas).
 *
 * No existe timestamp propio en Rating/Comment: la fecha que se muestra es
 * la de la instancia vivida que sostiene la reseña.
 */
public record ExperienceReviewDTO(
        String username,
        int stars,
        String text,
        LocalDate date
) {
}
