package com.openlab.mx.share.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Idempotencia de webhooks (spec §7): event_id PK; un evento procesado
 * jamás aplica efecto dos veces.
 */
@Entity
@Table(name = "stripe_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeEvent {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String type;

    private LocalDateTime processedAt;
}
