package com.openlab.mx.movo.experience.repository;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.ExperienceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    List<Experience> findByCreatorId(Long creatorId);
    List<Experience> findByStatus(ExperienceStatus status);
    List<Experience> findByStatusAndCategory(ExperienceStatus status, ExperienceCategory category);
}
