package com.openlab.mx.share.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.repository.SubrubroRepository;
import com.openlab.mx.share.interest.repository.UserInterestRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubrubroSeederTests {

    @Autowired
    private SubrubroRepository subrubros;

    @Autowired
    private UserInterestRepository userInterests;

    @Autowired
    private ExperienceRepository experiencias;

    /**
     * Todo borrado de subrubros empieza acá: {@code user_interests.subrubro_id}
     * es FK con RESTRICT, y {@link com.openlab.mx.share.interest.DemoInterestSeeder}
     * deja intereses de demo apuntando al catálogo. La transacción del test
     * revierte todo al terminar.
     */
    private void limpiarCatalogo() {
        desacoplarExperiencias();
        userInterests.deleteAll();
        // Flush explícito: Hibernate agrupa los DELETE y sin esto el de subrubros
        // puede salir primero y chocar contra la FK.
        userInterests.flush();
        subrubros.deleteAll();
    }

    /**
     * {@code experiences.subrubro_id} es FK RESTRICT desde que la tarjeta de
     * propuesta persiste el subtipo: sin esto, borrar el catálogo choca contra
     * las experiencias creadas por otros tests de la misma base.
     */
    private void desacoplarExperiencias() {
        experiencias.findAll().forEach(e -> e.setSubrubro(null));
        experiencias.flush();
    }

    @Test
    void siembraLaSemillaCompleta() {
        limpiarCatalogo();

        new SubrubroSeeder(subrubros).run();

        long count = subrubros.count();
        assertTrue(count >= 20, "semilla mínima esperada, hay " + count);
        assertTrue(subrubros.existsByNombreAndCategoria("Senderismo", "AVENTURA"));
        // La categoría debe coincidir EXACTA con ExperienceCategory.GASTRONOMIA
        // (sin acento): es un string libre y no lo valida ninguna FK.
        assertTrue(subrubros.existsByNombreAndCategoria("Degustación de vinos", "GASTRONOMIA"));
    }

    @Test
    void idempotenteNoDuplica() {
        var seeder = new SubrubroSeeder(subrubros);
        seeder.run();
        long antes = subrubros.count();

        seeder.run();

        assertEquals(antes, subrubros.count(), "re-ejecución no duplica filas");
    }

    @Test
    void agregaFilasFaltantes() {
        var seeder = new SubrubroSeeder(subrubros);
        seeder.run();
        long antes = subrubros.count();

        var senderismo = subrubros.findByCategoria("AVENTURA").stream()
            .filter(s -> "Senderismo".equals(s.getNombre()))
            .findFirst()
            .orElseThrow();
        // Senderismo es de los que el seeder de demo usa: hay que soltar la FK
        // antes de borrarlo, si no la base (con razón) lo impide.
        desacoplarExperiencias();
        userInterests.deleteAll();
        userInterests.flush();
        subrubros.delete(senderismo);
        assertFalse(subrubros.existsByNombreAndCategoria("Senderismo", "AVENTURA"));

        seeder.run();

        assertTrue(subrubros.existsByNombreAndCategoria("Senderismo", "AVENTURA"));
        assertEquals(antes, subrubros.count(), "vuelve al total sin duplicar");
    }
}
