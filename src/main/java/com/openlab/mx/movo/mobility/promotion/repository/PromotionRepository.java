package com.openlab.mx.movo.mobility.promotion.repository;

import com.openlab.mx.movo.mobility.promotion.entity.Promotion;
import com.openlab.mx.movo.mobility.promotion.entity.PromotionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByStatusOrderByExpiresAtDesc(PromotionStatus status);
    List<Promotion> findActiveByStatusAndStartsAtLessThanEqualAndExpiresAtGreaterThanEqual(
            PromotionStatus status, LocalDateTime now1, LocalDateTime now2);
}