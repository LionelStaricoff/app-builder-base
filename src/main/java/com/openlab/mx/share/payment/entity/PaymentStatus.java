package com.openlab.mx.share.payment.entity;

/**
 * Máquina de estados de spec §3.2: PENDING (sesión creada, pago no completado)
 * → HELD (autorizado sin capturar) → RELEASED | REFUNDED_FULL | REFUNDED_PARTIAL;
 * PENDING|HELD → FAILED (autorización muerta/expirada/declinada).
 */
public enum PaymentStatus {
    PENDING,
    HELD, RELEASED, REFUNDED_FULL, REFUNDED_PARTIAL
}
