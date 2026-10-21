package com.openlab.mx.share.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneId;import java.time.LocalDateTime;

/**
 * Registro de eventos de webhook ya procesados. {@code event_id} es la PK:
 * Didit reutiliza el event_id en los reintentos, así que la unicidad da
 * idempotencia gratis.
 */
@Entity
@Table(name = "didit_webhook_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiditWebhookEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @PrePersist
    void onPersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now(ZoneId.systemDefault());
        }
    }
}
