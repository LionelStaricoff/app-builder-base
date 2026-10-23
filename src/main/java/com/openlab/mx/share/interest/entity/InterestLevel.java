package com.openlab.mx.share.interest.entity;

/**
 * Qué tanto sabe el usuario de un subrubro. Sale del paso 2 del onboarding
 * ("¿Qué tanto sabes de cada uno?") y es insumo del matching futuro.
 */
public enum InterestLevel {

    CURIOSO("Curioso"),
    AFICIONADO("Aficionado"),
    EXPERTO("Experto");

    private final String label;

    InterestLevel(String label) {
        this.label = label;
    }

    /** Texto para la UI (el nombre del enum va en mayúsculas). */
    public String getLabel() {
        return label;
    }
}
