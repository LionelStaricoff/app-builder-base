package com.openlab.mx.share.payment.entity;

/**
 * Estado de la cuenta Stripe Connect del creador.
 */
public enum ConnectAccountStatus {
    /** Nunca se creó la cuenta Connect. */
    NOT_STARTED,
    /** El creador empezó el onboarding pero no lo completó. */
    ONBOARDING_INCOMPLETE,
    /** Cuenta activa y habilitada para recibir pagos. */
    ACTIVE,
    /** Cuenta restringida por Stripe (KYC incompleto, fraude, etc.). */
    RESTRICTED
}
