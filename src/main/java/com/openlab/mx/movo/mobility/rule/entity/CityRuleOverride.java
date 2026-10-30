package com.openlab.mx.movo.mobility.rule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * Override opcional por ciudad. Cualquier campo null significa "heredar del país"
 * (CountryRule). Nunca se inventa un valor por defecto: lo no configurado
 * explícitamente se toma del país.
 */
@Entity
@Table(name = "city_rule_overrides")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityRuleOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_rule_id", nullable = false)
    private CountryRule countryRule;

    @Column(name = "city_name", nullable = false, length = 128)
    private String cityName;

    /** Null = hereda la moneda del país. */
    @Column(name = "currency_code", length = 8)
    private String currencyCode;

    /** Null = hereda el mínimo del país. */
    @Column(name = "min_fare", precision = 19, scale = 2)
    private BigDecimal minFare;

    /** Null = hereda el requisito del país. */
    @Column(name = "requires_personal_identity")
    private Boolean requiresPersonalIdentity;

    /** Null = hereda el requisito del país. */
    @Column(name = "requires_vehicle")
    private Boolean requiresVehicle;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}