package com.openlab.mx.share.payment.entity;

import com.openlab.mx.share.user.entity.User;
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

import java.time.LocalDateTime;

/**
 * Cuenta Stripe Connect asociada a un usuario creador.
 * Guarda solo el identificador de Stripe y el estado necesario para la app.
 * NO se guardan datos bancarios ni documentos de identidad.
 */
@Entity
@Table(name = "connect_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    /** Identificador de la cuenta Stripe Connect (acct_xxx). */
    @Column(nullable = false, unique = true)
    private String stripeAccountId;

    /** Código ISO del país de la cuenta (ej. "MX", "US", "AR"). */
    @Column(nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConnectAccountStatus status = ConnectAccountStatus.NOT_STARTED;

    private LocalDateTime createdAt;

    private LocalDateTime lastSyncedAt;
}
