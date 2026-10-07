package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.ExperienceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceScheduleRepository extends JpaRepository<ExperienceSchedule, Long> {
    List<ExperienceSchedule> findByExperienceIdOrderByIdAsc(Long experienceId);
    void deleteByExperienceId(Long experienceId);
}
