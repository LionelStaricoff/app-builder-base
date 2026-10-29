package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.ExperienceSchedule;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Genera instancias futuras a partir de la agenda de una experiencia.
 * Al editar la agenda solo se regeneran las futuras (spec §4).
 */
@Service
@RequiredArgsConstructor
public class InstanceGenerationService {

    private final ExperienceInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;

    public List<ExperienceInstance> generateFor(Experience experience,
                                                List<ExperienceSchedule> schedules,
                                                LocalDate fromDate) {
        List<ExperienceInstance> instances = new ArrayList<>();
        for (ExperienceSchedule schedule : schedules) {
            if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
                continue;
            }
            if (experience.getRecurrenceType() == RecurrenceType.UNIQUE) {
                if (schedule.getDate() != null && !schedule.getDate().isBefore(fromDate)) {
                    instances.add(instance(experience, schedule.getDate(),
                            schedule.getStartTime(), schedule.getEndTime()));
                }
            } else if (schedule.getDayOfWeek() != null && schedule.getRangeStart() != null) {
                LocalDate end = schedule.getRangeEnd() != null ? schedule.getRangeEnd() : schedule.getRangeStart();
                for (LocalDate d = schedule.getRangeStart(); !d.isAfter(end); d = d.plusDays(1)) {
                    if (d.getDayOfWeek() == schedule.getDayOfWeek() && !d.isBefore(fromDate)) {
                        instances.add(instance(experience, d, schedule.getStartTime(), schedule.getEndTime()));
                    }
                }
            }
        }
        return instances;
    }

    /**
     * Regenera solo las instancias futuras LIBRES; las pasadas y las futuras
     * con reservas vigentes quedan intactas con su id (borrarlas rompería el
     * FK con reservations y destruiría reservas pagadas: esas se cancelan por
     * el flujo explícito de cancelación, nunca por edición). Tampoco duplica
     * un slot que sigue vigente con reservas.
     */
    public List<ExperienceInstance> regenerateFutures(Experience experience,
                                                      List<ExperienceSchedule> schedules,
                                                      List<ExperienceInstance> existing,
                                                      LocalDate today) {
        Set<Slot> bookedSlots = new HashSet<>();
        List<ExperienceInstance> free = new ArrayList<>();
        for (ExperienceInstance i : existing) {
            if (i.getDate().isBefore(today)) continue; // pasadas: intactas
            if (i.getId() != null && reservationRepository.existsByInstanceIdAndStatusNot(
                    i.getId(), ReservationStatus.CANCELADO)) {
                bookedSlots.add(new Slot(i.getDate(), i.getStartTime(), i.getEndTime()));
            } else if (i.getId() != null) {
                free.add(i);
            }
        }
        if (!free.isEmpty()) {
            instanceRepository.deleteAll(free);
        }
        return generateFor(experience, schedules, today).stream()
                .filter(inst -> !bookedSlots.contains(
                        new Slot(inst.getDate(), inst.getStartTime(), inst.getEndTime())))
                .toList();
    }

    /** Un slot fecha+horario: lo que una reserva vigente ocupa. */
    private record Slot(LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    private ExperienceInstance instance(Experience experience, LocalDate date,
                                        java.time.LocalTime start, java.time.LocalTime end) {
        return ExperienceInstance.builder()
                .experience(experience)
                .date(date)
                .startTime(start)
                .endTime(end)
                .status(InstanceStatus.SCHEDULED)
                .build();
    }
}
