package com.openlab.mx.share.experience.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyección del feed: todas las imágenes en orden para el carousel.
 * coverBase64 se mantiene (primera imagen o null) por compatibilidad.
 * currencyCode y stops son aditivos: los tests viejos que leen los
 * campos originales siguen pasando.
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
        boolean favorite,
        String currencyCode,
        List<ExperienceStopDTO> stops,
        String stopsJson
) {
}
