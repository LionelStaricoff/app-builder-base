package com.openlab.mx.share.reservation.entity;

public enum ReservationStatus {
    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    CANCELADO("Cancelada");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    /**
     * Etiqueta para mostrar al usuario, en lugar del nombre crudo del enum.
     *
     * @return texto legible, p. ej. {@code "Pendiente"}
     */
    public String getLabel() {
        return label;
    }
}
