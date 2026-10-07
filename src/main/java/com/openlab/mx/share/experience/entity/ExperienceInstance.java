package com.openlab.mx.share.experience.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * La "experiencia vivida": instancia concreta reservable (spec §4).
 */
@Entity
@Table(name = "experience_instances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "experience_id")
    private Experience experience;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InstanceStatus status = InstanceStatus.SCHEDULED;

    /** true cuando el creador canceló la fecha: chat en solo-lectura. */
    @Builder.Default
    private boolean archived = false;
}
