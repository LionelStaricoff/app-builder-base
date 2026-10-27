package com.openlab.mx.share.proposal.entity;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Borrador de propuesta con IA: solo el bloque de contenido (spec
 * docs/superpowers/specs/2026-10-26-ai-proposal-design.md §5). Varios por
 * usuario; la pantalla 3 muestra el último por updatedAt.
 */
@Entity
@Table(name = "experience_drafts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceCategory category;

    /** Qué incluye el precio. Texto libre, un ítem por línea. Nullable. */
    @Column(columnDefinition = "TEXT")
    private String includes;

    /** Qué tiene que llevar el huésped. Texto libre, un ítem por línea. Nullable. */
    @Column(columnDefinition = "TEXT")
    private String bring;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());

    /**
     * Arranca en el valor del caller (sin @PrePersist: el que inserta puede
     * fijarlo para ordenar); @PreUpdate lo refresca en cada modificación.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneId.systemDefault());

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
