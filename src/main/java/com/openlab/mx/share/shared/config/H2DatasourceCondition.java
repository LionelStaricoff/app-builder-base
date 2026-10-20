package com.openlab.mx.share.shared.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condición de bean que solo se cumple cuando la base de datos es H2.
 *
 * <p>Existe para que los datos precargados (usuarios demo) vivan únicamente en
 * la base de pruebas/local. Contra Postgres el bean NO se crea, así que no hay
 * forma de que un despliegue siembre cuentas demo en la base real: no depende
 * de que alguien recuerde activar o desactivar un flag.
 *
 * <p>El URL se lee ya resuelto del {@code Environment} (en main viene de
 * {@code SPRING_DATASOURCE_URL}).
 */
public class H2DatasourceCondition implements Condition {

    /** Prefijo de URL JDBC de H2 (en memoria o file-based). */
    private static final String H2_URL_PREFIX = "jdbc:h2:";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("spring.datasource.url");
        return isH2(url);
    }

    /**
     * ¿La URL dada apunta a H2? Función pura (separada de {@code matches}) para
     * poder testearla sin montar un contexto de Spring.
     *
     * @param url URL JDBC del datasource; {@code null} o vacío → {@code false}
     *             (fail-closed: sin datasource no se habilita nada).
     */
    static boolean isH2(String url) {
        return url != null && url.startsWith(H2_URL_PREFIX);
    }
}
