package com.openlab.mx.share.payment.entity;

import com.openlab.mx.share.reservation.entity.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago retenido en escrow: amount = price * attendees,
 * commission = amount * 5% (spec §5.3).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "reservation_id", unique = true)
    private Reservation reservation;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal commission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.HELD;

    private LocalDateTime heldAt;

    private LocalDateTime releasedAt;

    /** Lo que efectivamente cobra el creador: amount - commission. */
    @Column(precision = 10, scale = 2)
    private BigDecimal payout;

    /** Lo que se devolvió al participante al cancelar. */
    @Column(precision = 10, scale = 2)
    private BigDecimal refund;
}
