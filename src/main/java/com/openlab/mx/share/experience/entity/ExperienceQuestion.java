package com.openlab.mx.share.experience.entity;

import com.openlab.mx.share.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Pregunta pública de una persona interesada y la respuesta de la anfitriona.
 *
 * <p>Vive a nivel EXPERIENCIA (no de una fecha concreta): la duda —"¿puedo ir
 * aunque nunca haya tocado barro?"— aplica a todas las salidas, y repetirla por
 * instancia fragmentaría la misma conversación en cada fecha.
 */
@Entity
@Table(name = "experience_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "experience_id")
    private Experience experience;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Respuesta de la anfitriona; {@code null} mientras nadie respondió. */
    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
