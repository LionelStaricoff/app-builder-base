package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.repository.CommentRepository;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.RatingRepository;
import com.openlab.mx.movo.payment.service.EscrowExperiencePort;
import com.openlab.mx.movo.payment.service.PayoutCurrencyPort;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EscrowExperienceAdapter implements EscrowExperiencePort, PayoutCurrencyPort {

    private final ExperienceInstanceRepository instanceRepository;
    private final RatingRepository ratingRepository;
    private final CommentRepository commentRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public boolean isInstanceCompleted(Long instanceId) {
        ExperienceInstance instance = instanceRepository.findById(instanceId).orElse(null);
        return instance != null && instance.getStatus() == InstanceStatus.COMPLETED;
    }

    @Override
    public boolean allConfirmedRated(Long instanceId) {
        var confirmadas = reservationRepository.findByInstanceIdAndStatus(instanceId, ReservationStatus.CONFIRMADA);
        if (confirmadas.isEmpty()) {
            return false;
        }
        Set<Long> conRating = ratingRepository.findByInstanceId(instanceId).stream()
                .map(r -> r.getUser().getId()).collect(Collectors.toSet());
        Set<Long> conComentario = commentRepository.findByInstanceId(instanceId).stream()
                .map(c -> c.getUser().getId()).collect(Collectors.toSet());
        return confirmadas.stream().allMatch(r ->
                conRating.contains(r.getUser().getId()) && conComentario.contains(r.getUser().getId()));
    }

    @Override
    public java.util.Optional<com.openlab.mx.movo.user.entity.User> experienceCreator(Long instanceId) {
        ExperienceInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getExperience() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(instance.getExperience().getCreator());
    }

    @Override
    public String experienceName(Long instanceId) {
        ExperienceInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getExperience() == null) {
            return null;
        }
        return instance.getExperience().getName();
    }

    @Override
    public java.util.Optional<String> currencyOfReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getInstance() == null
                || reservation.getInstance().getExperience() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(reservation.getInstance().getExperience().getCurrencyCode());
    }
}
