package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.dto.ExperienceQuestionDTO;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceQuestion;
import com.openlab.mx.movo.experience.repository.ExperienceQuestionRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Preguntas públicas del detalle: las hace cualquiera con cuenta y las responde
 * la anfitriona.
 *
 * <p>La respuesta es única (no hay hilo): una duda general se contesta una vez y
 * queda para todas las personas que lean el detalle después.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final int MIN_BODY = 3;
    private static final int MAX_BODY = 500;

    private final ExperienceRepository experienceRepository;
    private final ExperienceQuestionRepository questionRepository;

    /** Guarda la pregunta y devuelve su id. */
    @Transactional
    public Long ask(Long experienceId, User asker, String body) {
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new DomainException("Experiencia no encontrada"));
        if (esAnfitriona(experience, asker)) {
            throw new DomainException("Las preguntas las hacen las personas interesadas: vos respondés");
        }
        String texto = body == null ? "" : body.trim();
        if (texto.length() < MIN_BODY || texto.length() > MAX_BODY) {
            throw new DomainException("La pregunta tiene que tener entre "
                    + MIN_BODY + " y " + MAX_BODY + " caracteres");
        }
        ExperienceQuestion question = ExperienceQuestion.builder()
                .experience(experience)
                .user(asker)
                .body(texto)
                .build();
        return questionRepository.save(question).getId();
    }

    /**
     * Responde una pregunta. Devuelve el id de la experiencia, que es a donde
     * hay que volver (tambien cuando la respuesta es rechazada).
     */
    @Transactional
    public Long answer(Long questionId, User creator, String answer) {
        ExperienceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new DomainException("Esa pregunta ya no existe"));
        Experience experience = question.getExperience();
        if (!esAnfitriona(experience, creator)) {
            throw new DomainException("Solo quien organiza la experiencia puede responder");
        }
        if (question.getAnswer() != null && !question.getAnswer().isBlank()) {
            throw new DomainException("Esa pregunta ya tiene respuesta");
        }
        String texto = answer == null ? "" : answer.trim();
        if (texto.isEmpty() || texto.length() > MAX_BODY) {
            throw new DomainException("La respuesta tiene que tener entre 1 y "
                    + MAX_BODY + " caracteres");
        }
        question.setAnswer(texto);
        question.setAnsweredAt(LocalDateTime.now(ZoneId.systemDefault()));
        return experience.getId();
    }

    /** Experiencia dueña de la pregunta (para el redirect del POST). */
    @Transactional(readOnly = true)
    public Long experienceIdOf(Long questionId) {
        return questionRepository.findById(questionId)
                .map(q -> q.getExperience().getId())
                .orElseThrow(() -> new DomainException("Esa pregunta ya no existe"));
    }

    /** Preguntas del detalle ya resueltas a DTO (sin proxies lazy en la vista). */
    @Transactional(readOnly = true)
    public List<ExperienceQuestionDTO> questionsFor(Long experienceId) {
        return questionRepository.findByExperienceIdOrderByCreatedAtDescIdDesc(experienceId).stream()
                .map(q -> new ExperienceQuestionDTO(
                        q.getId(),
                        q.getUser().getUsername(),
                        q.getUser().getAvatarBase64(),
                        q.getBody(),
                        q.getCreatedAt(),
                        q.getAnswer(),
                        q.getAnsweredAt()))
                .toList();
    }

    private boolean esAnfitriona(Experience experience, User user) {
        return experience.getCreator() != null
                && user != null
                && experience.getCreator().getId().equals(user.getId());
    }
}
