package com.openlab.mx.share.interest.entity;

import com.openlab.mx.share.experience.entity.Subrubro;
import com.openlab.mx.share.user.entity.User;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Interés declarado por un usuario sobre un subrubro.
 *
 * <p>Es una entidad intermedia y NO un {@code @ManyToMany}: la relación lleva
 * un atributo propio ({@link #level}). Un {@code @ManyToMany} no puede
 * guardarlo. El índice único {@code (user_id, subrubro_id)} vive en la
 * migración; acá solo se declara la tabla.
 */
@Entity
@Table(name = "user_interests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subrubro_id", nullable = false)
    private Subrubro subrubro;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterestLevel level = InterestLevel.CURIOSO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
