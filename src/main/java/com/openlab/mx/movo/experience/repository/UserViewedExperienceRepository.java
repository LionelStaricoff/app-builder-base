package com.openlab.mx.movo.experience.repository;

import com.openlab.mx.movo.experience.entity.UserViewedExperience;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserViewedExperienceRepository extends JpaRepository<UserViewedExperience, Long> {

    /** Busca si un usuario ya ha visto una experiencia. */
    boolean existsByUserIdAndExperienceId(Long userId, Long experienceId);

    /** Obtiene las experiencias vistas por un usuario. */
    List<UserViewedExperience> findByUserIdOrderByViewedAtDesc(Long userId);

    /** Obtiene los IDs de experiencias vistas por un usuario. */
    List<Long> findExperienceIdsByUserId(Long userId);
}