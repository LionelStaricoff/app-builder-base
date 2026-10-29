package com.openlab.mx.movo.experience.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una fila de agenda: UNIQUE usa date; WEEKLY usa dayOfWeek + range.
 */
public record ScheduleEntryRequest(
        LocalDate date,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate rangeStart,
        LocalDate rangeEnd
) {
}
