package com.openlab.mx.movo.mobility.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "ride_id")
    private Long rideId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LedgerEntryType type;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Column(name = "debt_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal debtDelta = BigDecimal.ZERO;

    @NotNull
    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode;

    @NotNull
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (debtDelta == null) debtDelta = BigDecimal.ZERO;
        if (amount == null) amount = BigDecimal.ZERO;
    }

    public boolean isDebt() {
        return type == LedgerEntryType.CASH_COMMISSION_DEBT;
    }
}