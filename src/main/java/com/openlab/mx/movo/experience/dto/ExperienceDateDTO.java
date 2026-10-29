package com.openlab.mx.movo.experience.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Una fecha reservable de la experiencia, con lo que la card necesita para
 * pintar cupos y avatares sin otra llamada.
 *
 * <p>La capacidad vive en la experiencia, no en la instancia: todas las filas
 * comparten {@code maxCapacity} y solo cambian las reservas.
 */
public record ExperienceDateDTO(
        Long instanceId,
        LocalDate date,
        LocalTime startTime,
        int maxCapacity,
        int availableCapacity,
        int reservedCount,
        List<String> reservedAvatars,
        Integer refundPercent
) {
}
