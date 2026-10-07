package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.ExperienceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceImageRepository extends JpaRepository<ExperienceImage, Long> {
    List<ExperienceImage> findByExperienceIdAndPositionOrderByIdAsc(Long experienceId, int position);
    List<ExperienceImage> findByExperienceIdOrderByIdAsc(Long experienceId);
    void deleteByExperienceId(Long experienceId);
}
