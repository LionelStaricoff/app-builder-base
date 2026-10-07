package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExperienceInstanceRepository extends JpaRepository<ExperienceInstance, Long> {
    List<ExperienceInstance> findByExperienceIdAndDateGreaterThanEqualOrderByDateAsc(Long experienceId, LocalDate date);
    List<ExperienceInstance> findByStatus(InstanceStatus status);
    List<ExperienceInstance> findByExperienceIdOrderByDateAsc(Long experienceId);
}
