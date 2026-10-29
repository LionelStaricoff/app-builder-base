package com.openlab.mx.movo.experience.repository;

import com.openlab.mx.movo.experience.entity.ExperienceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceScheduleRepository extends JpaRepository<ExperienceSchedule, Long> {
    List<ExperienceSchedule> findByExperienceIdOrderByIdAsc(Long experienceId);
    void deleteByExperienceId(Long experienceId);
}
