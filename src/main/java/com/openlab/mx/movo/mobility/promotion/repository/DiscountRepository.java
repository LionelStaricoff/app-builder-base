package com.openlab.mx.movo.mobility.promotion.repository;

import com.openlab.mx.movo.mobility.promotion.entity.Discount;
import com.openlab.mx.movo.mobility.promotion.entity.DiscountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    List<Discount> findByPassengerIdOrderByExpiresAtDesc(Long passengerId);
    List<Discount> findByStatus(DiscountStatus status);
}