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
@Table(name = "passenger_discounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscountType type;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "value_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(name = "passenger_id")
    private Long passengerId;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "max_usage")
    private Integer maxUsage;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscountStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = DiscountStatus.ACTIVE;
        if (usageCount == null) usageCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean usable(LocalDateTime now) {
        if (status != DiscountStatus.ACTIVE) return false;
        if (now.isBefore(startsAt) || now.isAfter(expiresAt)) return false;
        if (maxUsage != null && usageCount >= maxUsage) return false;
        return true;
    }
}