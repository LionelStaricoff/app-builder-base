package com.openlab.mx.share.favorite.service;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.favorite.entity.Favorite;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
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
    void testListSoloLosDelUsuario() {
        Favorite favorite = Favorite.builder().id(1L).experience(experience()).build();
        when(favoriteRepository.findByUserIdOrderByIdDesc(7L)).thenReturn(List.of(favorite));
        when(feedService.dtoFor(any(Experience.class), eq(7L)))
                .thenReturn(new com.openlab.mx.share.experience.dto.ExperienceFeedDTO(
                        3L, "n", "d", "c", "1.0, 2.0", 60,
                        java.time.LocalDate.of(2026, 12, 1),
                        java.math.BigDecimal.TEN, 4.5, true));

        var result = favoriteService.listFavorites(user());

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).id());
        verify(feedService).dtoFor(favorite.getExperience(), 7L);
    }
}
