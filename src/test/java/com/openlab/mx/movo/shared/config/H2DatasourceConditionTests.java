package com.openlab.mx.movo.shared.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.openlab.mx.movo.shared.DataSeeder;
import com.openlab.mx.movo.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El seeder de datos precargados SOLO debe existir contra H2. Esta clase es el
 * candado: si alguien cambia la condición, este test lo frena.
 */
class H2DatasourceConditionTests {

    @Test
    @DisplayName("H2 en memoria (tests): el seeder de datos precargados SÍ se crea")
    void h2EnMemoriaHabilitaElSeeder() {
        assertTrue(H2DatasourceCondition.isH2("jdbc:h2:mem:sharedtest;DB_CLOSE_DELAY=-1"));
    }

    @Test
    @DisplayName("H2 file-based (dev local): el seeder SÍ se crea")
    void h2FileBasedHabilitaElSeeder() {
        assertTrue(H2DatasourceCondition.isH2("jdbc:h2:file:./data/share"));
    }

    @Test
    @DisplayName("Postgres (producción): el seeder de datos precargados NO se crea")
    void postgresDeshabilitaElSeeder() {
        assertFalse(H2DatasourceCondition.isH2("jdbc:postgresql://db-host:5432/share"));
    }

    @Test
    @DisplayName("Sin datasource configurado: el seeder NO se crea (fail-closed)")
    void sinDatasourceDeshabilitaElSeeder() {
        assertFalse(H2DatasourceCondition.isH2(null));
        assertFalse(H2DatasourceCondition.isH2(""));
    }

    /**
     * Prueba end-to-end del candado: el BEAN DataSeeder no llega a existir
     * cuando el datasource es Postgres. No alcanza con la lógica de la
     * condición: importa que el bean no se registre.
     */
    @Test
    @DisplayName("Con Postgres, el bean DataSeeder NO existe (no hay seeding posible)")
    void contraPostgresElBeanNoExiste() {
        contextoConDatasource("jdbc:postgresql://db-host:5432/share")
                .run(ctx -> org.assertj.core.api.Assertions.assertThat(ctx)
                        .doesNotHaveBean(DataSeeder.class));
    }

    @Test
    @DisplayName("Con H2, el bean DataSeeder existe")
    void contraH2ElBeanExiste() {
        contextoConDatasource("jdbc:h2:mem:sharedtest")
                .run(ctx -> org.assertj.core.api.Assertions.assertThat(ctx)
                        .hasSingleBean(DataSeeder.class));
    }

    private ApplicationContextRunner contextoConDatasource(String url) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        return new ApplicationContextRunner()
                .withPropertyValues("spring.datasource.url=" + url, "app.demo.password=Clave1234!")
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(PasswordEncoder.class, () -> encoder)
                .withUserConfiguration(DataSeeder.class);
    }
}
