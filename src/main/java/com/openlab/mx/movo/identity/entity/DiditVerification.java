package com.openlab.mx.movo.identity.entity;

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

import java.time.ZoneId;import java.time.LocalDateTime;

/**
 * Una fila por usuario (UNIQUE user_id): el back es source of truth del
 * estado de verificación. Máquina de estados:
 *
 * <pre>
 * IN_PROGRESS → VERIFIED | REJECTED | PENDING_MANUAL | EXPIRED
 * REJECTED / EXPIRED → IN_PROGRESS (restart con nueva session_id)
 * VERIFIED → terminal (nunca se reinicia ni degrada)
 * </pre>
 */
@Entity
@Table(name = "didit_verifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiditVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "didit_session_id")
    private String diditSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    /** Último literal crudo de Didit, para trazabilidad. */
    @Column(name = "didit_status")
    private String diditStatus;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Nueva verificación en curso para un usuario sin fila previa. */
    public static DiditVerification started(Long userId, String sessionId) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        return DiditVerification.builder()
                .userId(userId)
                .diditSessionId(sessionId)
                .status(VerificationStatus.IN_PROGRESS)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Reintento: sesión nueva, decisiones limpias. Permitido desde cualquier
     * estado no verificado (REJECTED/EXPIRED típicamente; IN_PROGRESS si el
     * usuario volvió al botón sin haber completado el flujo hosteado).
     *
     * @throws IllegalStateException si VERIFIED (terminal, no se reinicia).
     */
    public void restart(String newSessionId) {
        if (status == VerificationStatus.VERIFIED) {
            throw new IllegalStateException("VERIFIED es terminal: no se reinicia");
        }
        this.status = VerificationStatus.IN_PROGRESS;
        this.diditSessionId = newSessionId;
        this.diditStatus = null;
        this.verifiedAt = null;
        this.decidedAt = null;
        this.updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    /**
     * Aplica la decisión del webhook. Un VERIFIED nunca degrada aunque
     * llegue un evento tardío contradictorio.
     */
    public void applyDecision(VerificationStatus newStatus, String diditStatusRaw) {
        if (status == VerificationStatus.VERIFIED && newStatus != VerificationStatus.VERIFIED) {
            return;
        }
        this.status = newStatus;
        this.diditStatus = diditStatusRaw;
        this.decidedAt = LocalDateTime.now(ZoneId.systemDefault());
        if (newStatus == VerificationStatus.VERIFIED) {
            this.verifiedAt = this.decidedAt;
        } else {
            this.verifiedAt = null;
        }
        this.updatedAt = this.decidedAt;
    }

    public boolean isVerified() {
        return status == VerificationStatus.VERIFIED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneId.systemDefault());
        }
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
