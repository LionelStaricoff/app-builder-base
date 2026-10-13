package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.ExperienceStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceStopRepository extends JpaRepository<ExperienceStop, Long> {
    List<ExperienceStop> findByExperienceIdOrderByPositionAscIdAsc(Long experienceId);
    void deleteByExperienceId(Long experienceId);
}
