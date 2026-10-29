package com.openlab.mx.movo.payment.entity;

/**
 * Estado del desembolso al creador vía Stripe Connect.
 */
public enum PayoutStatus {
    /** El dinero está capturado en la plataforma pero aún no se transfirió. */
    PENDING,
    /** El transfer está en proceso. */
    PROCESSING,
    /** El transfer se completó exitosamente. */
    COMPLETED,
    /** El transfer falló. Reintentable. */
    FAILED
}
