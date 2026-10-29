package com.openlab.mx.movo.favorite.repository;

import com.openlab.mx.movo.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    boolean existsByExperienceIdAndUserId(Long experienceId, Long userId);
    Optional<Favorite> findByExperienceIdAndUserId(Long experienceId, Long userId);
    List<Favorite> findByUserIdOrderByIdDesc(Long userId);
    void deleteByExperienceIdAndUserId(Long experienceId, Long userId);
}
