package com.openlab.mx.share.shared.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    private static final DateTimeFormatter FECHA_CORTA =
            DateTimeFormatter.ofPattern("dd MMM", ESPANOL);

    private static final DateTimeFormatter MES_ANIO =
            DateTimeFormatter.ofPattern("MMM yyyy", ESPANOL);

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

    /**
     * Fecha corta en el formato de la app: día y mes abreviado ({@code "24 oct"}).
     *
     * <p>El locale está fijo en castellano a propósito: la app es monolingüe y
     * {@code #temporals.format} sin locale toma el del navegador, así que un
     * navegador en inglés mostraría {@code "24 Oct"} en medio de la UI en español.</p>
     *
     * @param fecha fecha a mostrar (null devuelve cadena vacía)
     * @return fecha corta, p. ej. {@code "24 oct"}
     */
    public static String fechaCorta(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FECHA_CORTA);
    }

    /**
     * Mes y año en el formato de la app ({@code "oct 2026"}), con el mismo
     * criterio de locale fijo que {@link #fechaCorta(LocalDate)}.
     *
     * @param fecha fecha a mostrar (null devuelve cadena vacía)
     * @return mes y año, p. ej. {@code "oct 2026"}
     */
    public static String mesAnio(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(MES_ANIO);
    }
}
