package com.openlab.mx.share.favorite.service;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.CommentRepository;
import com.openlab.mx.share.experience.repository.RatingRepository;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.favorite.entity.Favorite;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private FeedService feedService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    private User user() {
        return User.builder().id(7L).username("u").build();
    }

    private Experience experience() {
        return Experience.builder().id(3L).build();
    }

    @Test
    void testAddEsIdempotente() {
        when(favoriteRepository.findByExperienceIdAndUserId(3L, 7L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Favorite.builder().id(1L).build()));
        when(feedService.experienceRef(3L)).thenReturn(experience());

        favoriteService.add(3L, user());
        favoriteService.add(3L, user()); // segunda vez: ya existe, no duplica

        verify(favoriteRepository, times(1)).save(any(Favorite.class));
    }

    @Test
    void testRemoveInexistenteNoLanza() {
        when(favoriteRepository.findByExperienceIdAndUserId(3L, 7L))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> favoriteService.remove(3L, user()));
        verify(favoriteRepository, never()).delete(any(Favorite.class));
    }

    @Test
    void testRemoveBorraElEncontrado() {
        Favorite existing = Favorite.builder().id(9L).build();
        when(favoriteRepository.findByExperienceIdAndUserId(3L, 7L))
                .thenReturn(Optional.of(existing));

        favoriteService.remove(3L, user());

        verify(favoriteRepository).delete(existing);
    }

    @Test
    void n() {
        Favorite favorite = Favorite.builder().id(1L).experience(experience()).build();
        when(favoriteRepository.findByUserIdOrderByIdDesc(7L)).thenReturn(List.of(favorite));
        when(feedService.dtoFor(any(Experience.class), eq(7L)))
                .thenReturn(new com.openlab.mx.share.experience.dto.ExperienceFeedDTO(
                        3L, "n", "d", "c", List.of("c"), "1.0, 2.0", 60,
                        java.time.LocalDate.of(2026, 12, 1),
                        java.math.BigDecimal.TEN, 4.5, true, "MXN", List.of(), "[]",
                        "creador", null, 5, 2, 3, List.of()));

        var result = favoriteService.listFavorites(user());

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).id());
        verify(feedService).dtoFor(favorite.getExperience(), 7L);
    }

    @Test
    void testListLivedSoloCompletadas() {
        ExperienceInstance completada = ExperienceInstance.builder()
                .id(10L)
                .experience(Experience.builder().id(3L).name("Tour terminado").build())
                .date(java.time.LocalDate.of(2026, 11, 1))
                .startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(12, 0))
                .status(InstanceStatus.COMPLETED)
                .build();
        ExperienceInstance pendiente = ExperienceInstance.builder()
                .id(11L)
                .experience(Experience.builder().id(4L).name("Tour futuro").build())
                .date(java.time.LocalDate.of(2026, 12, 1))
                .startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED)
                .build();
        when(reservationRepository.findByUserIdAndStatus(7L, ReservationStatus.CONFIRMADA))
                .thenReturn(List.of(
                        Reservation.builder().id(1L).instance(completada).user(user())
                                .attendees(1).status(ReservationStatus.CONFIRMADA).build(),
                        Reservation.builder().id(2L).instance(pendiente).user(user())
                                .attendees(1).status(ReservationStatus.CONFIRMADA).build()));
        when(ratingRepository.findByInstanceIdAndUserId(10L, 7L))
                .thenReturn(Optional.of(com.openlab.mx.share.experience.entity.Rating.builder()
                        .instance(completada).user(user()).stars(5).build()));
        when(commentRepository.findByInstanceIdAndUserId(10L, 7L))
                .thenReturn(Optional.of(com.openlab.mx.share.experience.entity.Comment.builder()
                        .instance(completada).user(user()).text("Genial").build()));

        var lived = favoriteService.listLived(user());

        assertEquals(1, lived.size(), "solo instancias COMPLETED");
        assertEquals("Tour terminado", lived.get(0).experienceName());
        assertEquals(5, lived.get(0).stars());
        assertEquals("Genial", lived.get(0).comment());
    }
}
