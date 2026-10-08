package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.ExperienceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExperienceImageRepository extends JpaRepository<ExperienceImage, Long> {
    List<ExperienceImage> findByExperienceIdAndPositionOrderByIdAsc(Long experienceId, int position);
    List<ExperienceImage> findByExperienceIdOrderByIdAsc(Long experienceId);
    Optional<ExperienceImage> findFirstByExperienceIdOrderByPositionAscIdAsc(Long experienceId);
    void deleteByExperienceId(Long experienceId);
}
