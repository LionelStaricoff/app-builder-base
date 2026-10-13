package com.openlab.mx.share.experience.dto;

/** Parada del recorrido para el feed/detalle (sin lazy, lista para JSON y Thymeleaf). */
public record ExperienceStopDTO(
        int position,
        String placeName,
        String city,
        String country,
        double lat,
        double lng
) {
}
