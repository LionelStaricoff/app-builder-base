package com.openlab.mx.movo.experience.repository;

import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ExperienceInstanceRepository extends JpaRepository<ExperienceInstance, Long> {
    List<ExperienceInstance> findByExperienceIdAndDateGreaterThanEqualOrderByDateAsc(Long experienceId, LocalDate date);
    List<ExperienceInstance> findByStatus(InstanceStatus status);
    List<ExperienceInstance> findByExperienceIdOrderByDateAsc(Long experienceId);
    Optional<ExperienceInstance> findFirstByExperienceIdAndDateGreaterThanEqualAndStatusOrderByDateAsc(
            Long experienceId, LocalDate date, InstanceStatus status);

    /** Terminadas: la fecha ya pasó (spec §8 — job de finalización). */
    @Query("select i from ExperienceInstance i where i.status = :status " +
            "and (i.date < :hoy or (i.date = :hoy and i.endTime <= :hora))")
    List<ExperienceInstance> findFinished(@Param("status") InstanceStatus status,
                                          @Param("hoy") LocalDate hoy,
                                          @Param("hora") LocalTime hora);

    /** Ya iniciadas (spec §8 — job de cupo mínimo). */
    @Query("select i from ExperienceInstance i where i.status = :status " +
            "and (i.date < :hoy or (i.date = :hoy and i.startTime <= :hora))")
    List<ExperienceInstance> findStarted(@Param("status") InstanceStatus status,
                                         @Param("hoy") LocalDate hoy,
                                         @Param("hora") LocalTime hora);
}
