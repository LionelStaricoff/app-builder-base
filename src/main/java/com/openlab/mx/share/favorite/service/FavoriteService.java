package com.openlab.mx.share.favorite.service;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.entity.Comment;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.Rating;
import com.openlab.mx.share.experience.repository.CommentRepository;
import com.openlab.mx.share.experience.repository.RatingRepository;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.favorite.dto.LivedExperienceResponse;
import com.openlab.mx.share.favorite.entity.Favorite;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final FeedService feedService;
    private final ReservationRepository reservationRepository;
    private final RatingRepository ratingRepository;
    private final CommentRepository commentRepository;

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

    /** Experiencias Vividas (spec §5.2): instancias COMPLETED con la evaluación del usuario. */
    public List<LivedExperienceResponse> listLived(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        List<LivedExperienceResponse> lived = new ArrayList<>();
        reservationRepository.findByUserIdAndStatus(user.getId(), ReservationStatus.CONFIRMADA)
                .forEach(reservation -> {
                    ExperienceInstance instance = reservation.getInstance();
                    if (instance == null || instance.getStatus() != InstanceStatus.COMPLETED) {
                        return;
                    }
                    int stars = ratingRepository
                            .findByInstanceIdAndUserId(instance.getId(), user.getId())
                            .map(Rating::getStars).orElse(0);
                    String comentario = commentRepository
                            .findByInstanceIdAndUserId(instance.getId(), user.getId())
                            .map(Comment::getText).orElse("");
                    lived.add(new LivedExperienceResponse(
                            instance.getExperience().getId(),
                            instance.getExperience().getName(),
                            instance.getDate(), stars, comentario));
                });
        lived.sort(Comparator.comparing(LivedExperienceResponse::date,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return lived;
    }
}
