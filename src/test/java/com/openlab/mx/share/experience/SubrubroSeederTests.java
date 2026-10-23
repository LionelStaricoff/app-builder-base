package com.openlab.mx.share.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openlab.mx.share.experience.repository.SubrubroRepository;

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

    @Test
    void siembraLaSemillaCompleta() {
        subrubros.deleteAll();

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
        subrubros.delete(senderismo);
        assertFalse(subrubros.existsByNombreAndCategoria("Senderismo", "AVENTURA"));

        seeder.run();

        assertTrue(subrubros.existsByNombreAndCategoria("Senderismo", "AVENTURA"));
        assertEquals(antes, subrubros.count(), "vuelve al total sin duplicar");
    }
}
