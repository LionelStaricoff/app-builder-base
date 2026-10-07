package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByInstanceId(Long instanceId);

    Optional<Rating> findByInstanceIdAndUserId(Long instanceId, Long userId);

    boolean existsByInstanceIdAndUserId(Long instanceId, Long userId);

    /** Promedio de estrellas de una experiencia completa. */
    @Query("select avg(r.stars) from Rating r where r.instance.experience.id = :experienceId")
    Double averageForExperience(@Param("experienceId") Long experienceId);
}
