package com.openlab.mx.movo.mobility.promotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String name;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("1.00")
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @NotNull
    @Column(name = "target_passenger_count", nullable = false)
    private Integer targetPassengerCount;

    @NotBlank
    @Column(name = "passenger_counting_rule", nullable = false, length = 512)
    private String passengerCountingRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromotionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = PromotionStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActiveNow(LocalDateTime now) {
        return status == PromotionStatus.ACTIVE
                && !now.isBefore(startsAt)
                && !now.isAfter(expiresAt);
    }
}