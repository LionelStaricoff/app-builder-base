package com.openlab.mx.share.experience.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyección del feed: todas las imágenes en orden para el carousel.
 * coverBase64 se mantiene (primera imagen o null) por compatibilidad.
 */
public record ExperienceFeedDTO(
        Long id,
        String name,
        String description,
        String coverBase64,
        List<String> images,
        String meetingPointLabel,
        int durationMinutes,
        LocalDate nextDate,
        BigDecimal price,
        double ratingAverage,
        boolean favorite
) {
}
