package com.openlab.mx.share.shared.ui;

import java.time.DayOfWeek;

/**
 * Etiquetas de UI para tipos del JDK que no pueden tener {@code getLabel()}
 * como los enums propios del proyecto (ver {@code ExperienceCategory}).
 *
 * <p>Existe para que ninguna plantilla muestre el nombre crudo de un enum
 * ({@code MONDAY}) al usuario final.</p>
 */
public final class UiLabels {

    private static final String[] DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    private UiLabels() {
    }

    /**
     * Nombre del día en castellano.
     *
     * @param dia día de la semana (null devuelve cadena vacía)
     * @return nombre capitalizado, p. ej. {@code "Lunes"}
     */
    public static String diaSemana(DayOfWeek dia) {
        return dia == null ? "" : DIAS[dia.getValue() - 1];
    }
}
