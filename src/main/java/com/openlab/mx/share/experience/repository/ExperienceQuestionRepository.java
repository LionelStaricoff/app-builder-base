package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.ExperienceQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienceQuestionRepository extends JpaRepository<ExperienceQuestion, Long> {

    /**
     * Preguntas del detalle: la más nueva primero (las sin responder también).
     *
     * <p>{@code @EntityGraph} trae el autor en la misma consulta: sin esto cada
     * pregunta dispara un SELECT extra a users para mostrar nombre y avatar.
     */
    @EntityGraph(attributePaths = "user")
    List<ExperienceQuestion> findByExperienceIdOrderByCreatedAtDescIdDesc(Long experienceId);

    long countByExperienceId(Long experienceId);
}
