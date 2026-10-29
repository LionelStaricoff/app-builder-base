package com.openlab.mx.movo.reservation.dto;

/**
 * Persona con reserva confirmada en una fecha futura: alimenta el "Quiénes van"
 * del detalle.
 *
 * <p>Solo nombre y avatar: la sección muestra que hay gente anotada, no el
 * historial de nadie. Nada de email ni id.
 */
public record ExperienceGoerDTO(
        String username,
        String avatarBase64
) {
}
