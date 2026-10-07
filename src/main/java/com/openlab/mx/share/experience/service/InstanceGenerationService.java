package com.openlab.mx.share.experience.service;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.ExperienceSchedule;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera instancias futuras a partir de la agenda de una experiencia.
 * Al editar la agenda solo se regeneran las futuras (spec §4).
 */
@Service
@RequiredArgsConstructor
public class InstanceGenerationService {

    private final ExperienceInstanceRepository instanceRepository;

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
     * Borra las instancias futuras existentes y genera las nuevas;
     * las pasadas quedan intactas con su id.
     */
    public List<ExperienceInstance> regenerateFutures(Experience experience,
                                                      List<ExperienceSchedule> schedules,
                                                      List<ExperienceInstance> existing,
                                                      LocalDate today) {
        List<ExperienceInstance> futures = existing.stream()
                .filter(i -> !i.getDate().isBefore(today))
                .toList();
        if (!futures.isEmpty()) {
            instanceRepository.deleteAll(futures);
        }
        return generateFor(experience, schedules, today);
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
