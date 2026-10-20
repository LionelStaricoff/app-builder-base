package com.openlab.mx.share.reservation.service;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Política de reembolso por anticipación (spec §5.4, Caso 1): cuando el
 * participante cancela su reserva, la plata que vuelve depende de cuánto
 * falte para el inicio de la experiencia.
 *
 * <p>Configurable por entorno con {@code REFUND_TIERS} (propiedad
 * {@code refund.tiers}): lista de tramos {@code horas:proporción} separados por
 * coma. Ej.: {@code 24:0.90,0:0.50} = "con más de 24 h de anticipación se
 * devuelve el 90%; con 24 h o menos, el 50%".
 *
 * <p>Reglas de los tramos:
 * <ul>
 *   <li>aplica el primero cuyo tiempo restante <b>supere</b> las horas indicadas;</li>
 *   <li>el último tramo es el respaldo: también cubre las fechas ya iniciadas;</li>
 *   <li>la proporción es lo que se devuelve al participante (0.90 = la
 *       plataforma retiene el 10%).</li>
 * </ul>
 */
@Component
public class RefundPolicy {

    /** Un tramo de la escalera: aplica cuando faltan más de {@code hours} horas. */
    public record Tier(int hours, BigDecimal rate) {
        public Tier {
            if (hours < 0) {
                throw new IllegalArgumentException("Las horas del tramo no pueden ser negativas: " + hours);
            }
            if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("La proporción del tramo debe estar entre 0 y 1: " + rate);
            }
        }
    }

    private final List<Tier> tiers;

    public RefundPolicy(@Value("${refund.tiers}") String config) {
        this.tiers = parse(config);
    }

    /** Tramos ordenados de mayor a menor anticipación. */
    public List<Tier> tiers() {
        return tiers;
    }

    /** Proporción a devolver al participante para una fecha. */
    public BigDecimal rateFor(ExperienceInstance instance) {
        return rateFor(LocalDateTime.of(instance.getDate(), instance.getStartTime()));
    }

    /** Proporción a devolver al participante para un inicio. */
    public BigDecimal rateFor(LocalDateTime inicio) {
        Duration restante = Duration.between(LocalDateTime.now(), inicio);
        for (Tier tier : tiers) {
            if (restante.compareTo(Duration.ofHours(tier.hours())) > 0) {
                return tier.rate();
            }
        }
        // Ningún tramo alcanzado (fecha ya iniciada): manda el último.
        return tiers.get(tiers.size() - 1).rate();
    }

    /** Porcentaje entero a devolver (90, 50, ...) para mostrar en la UI. */
    public int percentFor(ExperienceInstance instance) {
        return percent(rateFor(instance));
    }

    /** Porcentaje entero a devolver (90, 50, ...) para mostrar en la UI. */
    public int percentFor(LocalDateTime inicio) {
        return percent(rateFor(inicio));
    }

    /**
     * Política en texto para el cartel previo a reservar: una línea por tramo.
     * Ej.: "Si cancelás con más de 24 h de anticipación, se devuelve el 90%."
     */
    public List<String> lines() {
        List<String> lineas = new ArrayList<>();
        for (int i = 0; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            String ventana;
            if (i == 0) {
                ventana = "más de " + tier.hours() + " h de anticipación";
            } else if (i == tiers.size() - 1) {
                ventana = tiers.get(i - 1).hours() + " h o menos de anticipación";
            } else {
                ventana = "entre " + tier.hours() + " y " + tiers.get(i - 1).hours()
                        + " h de anticipación";
            }
            lineas.add("Si cancelás con " + ventana + ", se devuelve el "
                    + percent(tier.rate()) + "%.");
        }
        return lineas;
    }

    /** Convierte una proporción (0.90) en el porcentaje entero que se muestra (90). */
    public int percentOf(BigDecimal rate) {
        return rate.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int percent(BigDecimal rate) {
        return percentOf(rate);
    }

    private static List<Tier> parse(String config) {
        if (config == null || config.isBlank()) {
            throw new IllegalStateException("refund.tiers no puede estar vacío");
        }
        List<Tier> parsed = new ArrayList<>();
        for (String parte : config.split(",")) {
            String tramo = parte.trim();
            if (tramo.isEmpty()) {
                continue;
            }
            int separador = tramo.indexOf(':');
            if (separador < 0) {
                throw new IllegalStateException(
                        "Tramo de reembolso inválido: '" + tramo + "' (formato horas:proporción)");
            }
            int hours;
            BigDecimal rate;
            try {
                hours = Integer.parseInt(tramo.substring(0, separador).trim());
                rate = new BigDecimal(tramo.substring(separador + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Tramo de reembolso inválido: '" + tramo + "' (formato horas:proporción)", e);
            }
            try {
                parsed.add(new Tier(hours, rate));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Tramo de reembolso inválido: '" + tramo + "' (" + e.getMessage() + ")", e);
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException("refund.tiers no define ningún tramo");
        }
        // El orden de configuración no importa: la escalera va de mayor a menor
        // anticipación, así el primer tramo que se alcanza es el más generoso.
        parsed.sort(Comparator.comparingInt(Tier::hours).reversed());
        return List.copyOf(parsed);
    }
}
