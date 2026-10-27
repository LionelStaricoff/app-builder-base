package com.openlab.mx.share.shared;

/**
 * Parseo de ids que llegan como String desde query params o formularios.
 *
 * <p>La regla del repo (ver {@code ProposalService} con {@code draft=abc}):
 * basura o vacío ⇒ {@code null}, nunca una excepción de conversión que el
 * navegador no puede mostrar (400). El caller decide si null ignora o limpia.
 */
public final class Ids {

    private Ids() {
    }

    /** id numérico válido → Long; basura, vacío o negativo → null. */
    public static Long parse(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(valor.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
