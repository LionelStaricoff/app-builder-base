package com.openlab.mx.movo.experience.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "experiences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "creator_id")
    private com.openlab.mx.movo.user.entity.User creator;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Qué incluye el precio. Texto libre, un ítem por línea. Nullable. */
    @Column(columnDefinition = "TEXT")
    private String includes;

    /** Qué tiene que llevar el huésped. Texto libre, un ítem por línea. Nullable. */
    @Column(columnDefinition = "TEXT")
    private String bring;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private int minCapacity;

    @Column(nullable = false)
    private int maxCapacity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceType recurrenceType;

    @Column(nullable = false)
    private Double meetingLat;

    @Column(nullable = false)
    private Double meetingLng;

    /** Moneda en la que el creador fijó el precio (ISO, ej. "MXN"). Null = MXN legacy. */
    @Builder.Default
    private String currencyCode = "MXN";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExperienceStatus status = ExperienceStatus.ACTIVE;

    @Builder.Default
    private double ratingAverage = 0.0;

    /** Nullable: una experiencia puede no tener subrubro. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subrubro_id")
    private Subrubro subrubro;
}
