package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    List<Experience> findByCreatorId(Long creatorId);
    List<Experience> findByStatus(ExperienceStatus status);
}
