package com.openlab.mx.share.favorite.service;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.favorite.entity.Favorite;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final FeedService feedService;

    public void add(Long experienceId, User user) {
        if (favoriteRepository.findByExperienceIdAndUserId(experienceId, user.getId()).isPresent()) {
            return; // idempotente
        }
        favoriteRepository.save(Favorite.builder()
                .user(user)
                .experience(feedService.experienceRef(experienceId))
                .build());
    }

    public void remove(Long experienceId, User user) {
        favoriteRepository.findByExperienceIdAndUserId(experienceId, user.getId())
                .ifPresent(favoriteRepository::delete);
    }

    public List<ExperienceFeedDTO> listFavorites(User user) {
        return favoriteRepository.findByUserIdOrderByIdDesc(user.getId()).stream()
                .map(f -> feedService.dtoFor(f.getExperience(), user.getId()))
                .toList();
    }

    public boolean isFavorite(Long experienceId, Long userId) {
        return favoriteRepository.existsByExperienceIdAndUserId(experienceId, userId);
    }
}
