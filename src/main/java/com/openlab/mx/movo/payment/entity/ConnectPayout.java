package com.openlab.mx.movo.payment.entity;

import com.openlab.mx.movo.user.entity.User;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro financiero de un desembolso ejecutado a un creador vía Stripe Connect.
 * Cada payout está asociado a un Payment original y registra el transfer de Stripe.
 */
@Entity
@Table(name = "connect_payouts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User creator;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    /** Monto neto al creador (amount - commission). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Comisión de la plataforma. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal commission;

    /** Moneda ISO (ej. "MXN"). */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Identificador del transfer en Stripe (tr_xxx). */
    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    /** Código de error seguro (sin datos sensibles). */
    @Column(name = "failure_reason")
    private String failureReason;

    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    /** Clave de idempotencia para evitar duplicados. */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;
}
