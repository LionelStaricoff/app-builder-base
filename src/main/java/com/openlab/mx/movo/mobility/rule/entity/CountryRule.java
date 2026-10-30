package com.openlab.mx.movo.mobility.rule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Regla de movilidad vigente para un país. Es la fuente de verdad para moneda,
 * tarifa mínima y requisitos operativos del conductor. Sin ella, el país no puede
 * habilitar operación ni calcular tarifas.
 */
@Entity
@Table(name = "country_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 16)
    @Column(name = "country_code", nullable = false, unique = true, length = 16)
    private String countryCode;

    @Size(max = 128)
    @Column(name = "country_name", length = 128)
    private String countryName;

    @NotBlank
    @Size(max = 8)
    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "min_fare", nullable = false, precision = 19, scale = 2)
    private BigDecimal minFare;

    @Column(name = "requires_personal_identity", nullable = false)
    private boolean requiresPersonalIdentity = true;

    @Column(name = "requires_vehicle", nullable = false)
    private boolean requiresVehicle = true;

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