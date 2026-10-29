package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceSchedule;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceGenerationServiceTest {

    @Mock
    private ExperienceInstanceRepository instanceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private InstanceGenerationService service;

    private Experience experience(RecurrenceType type) {
        return Experience.builder()
                .id(1L)
                .name("Test")
                .recurrenceType(type)
                .build();
    }

    private ExperienceSchedule uniqueSchedule(LocalDate date, LocalTime start, LocalTime end) {
        return ExperienceSchedule.builder()
                .date(date).startTime(start).endTime(end)
                .build();
    }

    private ExperienceSchedule weeklySchedule(DayOfWeek day, LocalTime start, LocalTime end,
                                              LocalDate rangeStart, LocalDate rangeEnd) {
        return ExperienceSchedule.builder()
                .dayOfWeek(day).startTime(start).endTime(end)
                .rangeStart(rangeStart).rangeEnd(rangeEnd)
                .build();
    }

    @Test
    void testGeneraUnaInstanciaParaUnicaOcasion() {
        Experience e = experience(RecurrenceType.UNIQUE);
        List<ExperienceSchedule> schedules = List.of(
                uniqueSchedule(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0), LocalTime.of(12, 0)));

        var instances = service.generateFor(e, schedules, LocalDate.of(2026, 10, 7));

        assertEquals(1, instances.size());
        assertEquals(LocalDate.of(2026, 11, 1), instances.get(0).getDate());
        assertEquals(LocalTime.of(10, 0), instances.get(0).getStartTime());
        assertEquals(InstanceStatus.SCHEDULED, instances.get(0).getStatus());
        assertSame(e, instances.get(0).getExperience());
    }

    @Test
    void testGeneraInstanciasSemanales() {
        Experience e = experience(RecurrenceType.WEEKLY);
        List<ExperienceSchedule> schedules = List.of(
                weeklySchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0),
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)),
                weeklySchedule(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0),
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)));

        var instances = service.generateFor(e, schedules, LocalDate.of(2026, 10, 7));

        assertEquals(4, instances.size());
        List<LocalDate> dates = instances.stream().map(i -> i.getDate()).sorted().toList();
        assertEquals(List.of(
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 11)), dates);
    }

    @Test
    void testAlEditarAgendaSoloRegeneraFuturas() {
        Experience e = experience(RecurrenceType.UNIQUE);
        var past = com.openlab.mx.movo.experience.entity.ExperienceInstance.builder()
                .id(5L).experience(e).date(LocalDate.of(2026, 10, 1))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.COMPLETED).build();
        var future = com.openlab.mx.movo.experience.entity.ExperienceInstance.builder()
                .id(6L).experience(e).date(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED).build();
        List<ExperienceSchedule> newSchedules = List.of(
                uniqueSchedule(LocalDate.of(2026, 12, 1), LocalTime.of(15, 0), LocalTime.of(17, 0)));

        var regenerated = service.regenerateFutures(e, newSchedules,
                List.of(past, future), LocalDate.of(2026, 10, 7));

        ArgumentCaptor<List<com.openlab.mx.movo.experience.entity.ExperienceInstance>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(instanceRepository).deleteAll(captor.capture());
        assertEquals(1, captor.getValue().size(), "solo la futura se borra");
        assertSame(future, captor.getValue().get(0), "la futura vieja es la que se elimina");

        assertEquals(1, regenerated.size());
        assertEquals(LocalDate.of(2026, 12, 1), regenerated.get(0).getDate());
        assertNull(regenerated.get(0).getId(), "las nuevas van sin id");
    }

    @Test
    void testAlEditarAgendaConservaFuturasConReservasYSinDuplicar() {
        Experience e = experience(RecurrenceType.UNIQUE);
        var booked = com.openlab.mx.movo.experience.entity.ExperienceInstance.builder()
                .id(7L).experience(e).date(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED).build();
        var free = com.openlab.mx.movo.experience.entity.ExperienceInstance.builder()
                .id(8L).experience(e).date(LocalDate.of(2026, 11, 8))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED).build();
        // La agenda no cambia: mismo slot del sábado 1.
        List<ExperienceSchedule> sameSchedules = List.of(
                uniqueSchedule(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0), LocalTime.of(12, 0)),
                uniqueSchedule(LocalDate.of(2026, 11, 8), LocalTime.of(10, 0), LocalTime.of(12, 0)));
        when(reservationRepository.existsByInstanceIdAndStatusNot(7L, ReservationStatus.CANCELADO))
                .thenReturn(true);

        var regenerated = service.regenerateFutures(e, sameSchedules,
                List.of(booked, free), LocalDate.of(2026, 10, 7));

        ArgumentCaptor<List<com.openlab.mx.movo.experience.entity.ExperienceInstance>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(instanceRepository).deleteAll(captor.capture());
        assertEquals(List.of(free), captor.getValue(), "la reservada no se borra");
        assertEquals(1, regenerated.size(), "no duplica el slot que sigue vigente");
        assertEquals(LocalDate.of(2026, 11, 8), regenerated.get(0).getDate());
    }
}
