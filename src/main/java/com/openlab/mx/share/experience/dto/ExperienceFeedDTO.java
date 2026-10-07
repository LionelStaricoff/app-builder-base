package com.openlab.mx.share.experience.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección ligera del feed: solo la imagen tapa (spec §5.2).
 * Nunca incluye el arreglo completo de imágenes.
 */
public record ExperienceFeedDTO(
        Long id,
        String name,
        String description,
        String coverBase64,
        String meetingPointLabel,
        int durationMinutes,
        LocalDate nextDate,
        BigDecimal price,
        double ratingAverage,
        boolean favorite
) {
}
