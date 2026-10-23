package com.openlab.mx.share.payment.entity;

/**
 * Estado de la cuenta Stripe Connect del creador.
 */
public enum ConnectAccountStatus {
    /** Nunca se creó la cuenta Connect. */
    NOT_STARTED("Sin configurar"),
    /** El creador empezó el onboarding pero no lo completó. */
    ONBOARDING_INCOMPLETE("Onboarding pendiente"),
    /** Cuenta activa y habilitada para recibir pagos. */
    ACTIVE("Activa"),
    /** Cuenta restringida por Stripe (KYC incompleto, fraude, etc.). */
    RESTRICTED("Restringida");

    private final String label;

    ConnectAccountStatus(String label) {
        this.label = label;
    }

    /**
     * Etiqueta para mostrar al usuario, en lugar del nombre crudo del enum.
     *
     * @return texto legible, p. ej. {@code "Activa"}
     */
    public String getLabel() {
        return label;
    }

    /**
     * Etiqueta a partir del codigo ({@code name()}) que expone el mapa de estado
     * de Stripe Connect, que viaja como {@code String} y no como enum.
     *
     * @param codigo nombre del enum; si no coincide con ninguno se asume {@code NOT_STARTED}
     * @return texto legible para el usuario
     */
    public static String labelOf(String codigo) {
        if (codigo != null) {
            for (ConnectAccountStatus estado : values()) {
                if (estado.name().equals(codigo)) {
                    return estado.label;
                }
            }
        }
        return NOT_STARTED.label;
    }
}