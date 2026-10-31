package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.dto.ExperienceReviewDTO;
import com.openlab.mx.movo.experience.entity.Comment;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.entity.Rating;
import com.openlab.mx.movo.experience.repository.CommentRepository;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.experience.repository.RatingRepository;

import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Evaluación de instancias terminadas: estrella 1..5 + comentario,
 * actualiza el promedio de la experiencia y dispara el escrow (spec §5.3).
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final ExperienceInstanceRepository instanceRepository;
    private final ExperienceRepository experienceRepository;
    private final ReservationRepository reservationRepository;
    private final RatingRepository ratingRepository;
    private final CommentRepository commentRepository;
    @Transactional
    public void rate(Long instanceId, User u, int stars, String text) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("La estrella debe estar entre 1 y 5");
        }
        ExperienceInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
        if (instance.getStatus() != InstanceStatus.COMPLETED) {
            throw new DomainException("Solo se evalúa al terminar la experiencia");
        }
        boolean confirmada = reservationRepository.existsByInstanceIdAndUserIdAndStatus(
                instanceId, u.getId(), ReservationStatus.CONFIRMADA);
        if (!confirmada) {
            throw new DomainException("Solo los participantes confirmados pueden evaluar");
        }

        Rating rating = ratingRepository.findByInstanceIdAndUserId(instanceId, u.getId())
                .orElseGet(() -> Rating.builder().instance(instance).user(u).build());
        rating.setStars(stars);
        ratingRepository.save(rating);

        Comment comment = commentRepository.findByInstanceIdAndUserId(instanceId, u.getId())
                .orElseGet(() -> Comment.builder().instance(instance).user(u).build());
        comment.setText(text == null ? "" : text);
        commentRepository.save(comment);

        Experience experience = instance.getExperience();
        Double promedio = ratingRepository.averageForExperience(experience.getId());
        experience.setRatingAverage(promedio == null ? 0.0 : promedio);
        experienceRepository.save(experience);
    }

    /** Reseñas del detalle de la experiencia (estrella + texto), más nuevas primero. */
    public List<ExperienceReviewDTO> reviewsFor(Long experienceId) {
        return ratingRepository.reviewsForExperience(experienceId);
    }
}
