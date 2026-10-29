package com.openlab.mx.movo.experience.entity;

/**
 * Estado de publicación de una experiencia.
 *
 * <p>La UI imprime {@link #getLabel()}, nunca el nombre del enum: mostrar
 * {@code ACTIVE} a un usuario final es el mismo bug que mostrar
 * {@code MONDAY} en lugar de {@code Lunes}.</p>
 */
public enum ExperienceStatus {

    ACTIVE("Activa"),
    CANCELLED("Cancelada");

    private final String label;

    ExperienceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
