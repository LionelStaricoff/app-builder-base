package com.openlab.mx.share.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Fuente única de la hora del sistema.
 *
 * <p>Un bean {@link Clock} inyectable evita el antipatrón
 * {@code LocalDateTime.now(ZoneId.systemDefault())}: {@code now()} usa la zona del sistema JVM de
 * forma implícita y no es determinista en tests. Con un {@code Clock}
 * explícito, el código que calcula plazos o duraciones dice en qué zona
 * trabaja y los tests pueden congelar el tiempo.
 *
 * <p>La zona por defecto es la del sistema (comportamiento histórico de la
 * app). Se puede fijar con la variable de entorno {@code APP_TIME_ZONE}
 * usando un id IANA, p. ej. {@code America/Mexico_City}.
 */
@Configuration
public class TimeConfig {

    /**
     * @param timeZone zona IANA configurada; vacío = zona del sistema.
     */
    @Bean
    public Clock clock(@Value("${app.time-zone:}") String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return Clock.systemDefaultZone();
        }
        try {
            return Clock.system(ZoneId.of(timeZone.trim()));
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                    "app.time-zone inválido: '" + timeZone.trim() + "' (usá un id IANA, p. ej. America/Mexico_City)", e);
        }
    }
}
