package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.repository.CommentRepository;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.experience.repository.RatingRepository;

import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private ExperienceRepository experienceRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private RatingService ratingService;

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private Experience experience() {
        return Experience.builder()
                .id(1L)
                .creator(user(1L))
                .name("Tour")
                .price(BigDecimal.valueOf(190))
                .minCapacity(1)
                .maxCapacity(8)
                .ratingAverage(0.0)
                .build();
    }

    private ExperienceInstance instance(InstanceStatus status) {
        return ExperienceInstance.builder()
                .id(10L)
                .experience(experience())
                .date(LocalDate.of(2026, 12, 1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .status(status)
                .build();
    }

    private Reservation confirmada(ExperienceInstance instance, Long userId) {
        return Reservation.builder()
                .id(7L)
                .instance(instance)
                .user(user(userId))
                .attendees(2)
                .status(ReservationStatus.CONFIRMADA)
                .build();
    }

    private void participanteConfirmado(ExperienceInstance instance, Long userId) {
        when(reservationRepository.existsByInstanceIdAndUserIdAndStatus(
                10L, userId, ReservationStatus.CONFIRMADA)).thenReturn(true);
    }

    @Test
    void testRateRechazaFueraDeRango() {
        assertThrows(IllegalArgumentException.class,
                () -> ratingService.rate(10L, user(200L), 0, "malo"));
        assertThrows(IllegalArgumentException.class,
                () -> ratingService.rate(10L, user(200L), 6, "muy bueno"));
    }

    @Test
    void testRateActualizaPromedio() {
        ExperienceInstance instance = instance(InstanceStatus.COMPLETED);
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instance));
        participanteConfirmado(instance, 200L);
        when(ratingRepository.findByInstanceIdAndUserId(10L, 200L)).thenReturn(Optional.empty());
        when(commentRepository.findByInstanceIdAndUserId(10L, 200L)).thenReturn(Optional.empty());
        when(ratingRepository.averageForExperience(1L)).thenReturn(4.5);

        ratingService.rate(10L, user(200L), 5, "Excelente");

        assertEquals(4.5, instance.getExperience().getRatingAverage());
        verify(experienceRepository).save(any(Experience.class));
    }


}
