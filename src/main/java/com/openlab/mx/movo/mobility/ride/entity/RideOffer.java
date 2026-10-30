package com.openlab.mx.movo.mobility.ride.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ride_offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RideOfferStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = RideOfferStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        if (respondedAt == null && (status == RideOfferStatus.ACCEPTED
                || status == RideOfferStatus.REJECTED || status == RideOfferStatus.EXPIRED)) {
            respondedAt = LocalDateTime.now();
        }
    }
}