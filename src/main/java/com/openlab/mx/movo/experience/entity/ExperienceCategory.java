package com.openlab.mx.movo.experience.entity;

public enum ExperienceCategory {

    AVENTURA("Aventura"),
    GASTRONOMIA("Gastronomía"),
    CULTURAL("Cultural"),
    DEPORTIVA("Deportiva"),
    SOCIAL("Social");

    private final String label;

    ExperienceCategory(String label) {
        this.label = label;
    }

    /**
     * Texto para la UI. No cambia {@link #toString()}: el feed y los formularios
     * muestran el nombre del enum y así quedan.
     */
    public String getLabel() {
        return label;
    }
}
