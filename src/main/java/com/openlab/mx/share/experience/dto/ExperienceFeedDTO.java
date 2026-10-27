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
        /** Instancia de {@link #nextDate}: la card la usa para pedir fechas y cupos. */
        Long nextInstanceId,
        BigDecimal price,
        double ratingAverage,
        boolean favorite,
        String currencyCode,
        List<ExperienceStopDTO> stops,
        String stopsJson,
        /** Quién armó el viaje: nombre de usuario + avatar base64 (nullable en cuentas viejas). */
        String creatorName,
        String creatorAvatar,
        int maxCapacity,
        int availableCapacity,
        int reservedCount,
        List<String> reservedAvatars,
        /** Qué incluye el precio y qué llevar. Nullable: la card los omite si vienen vacíos. */
        String includes,
        String bring,
        /**
         * Distancia en km del usuario al punto de encuentro. Null cuando no hay
         * cómo medirla (sin ubicación del usuario o sin coordenadas del punto):
         * la card entonces no muestra el chip.
         */
        Double distanceKm
) {
}
