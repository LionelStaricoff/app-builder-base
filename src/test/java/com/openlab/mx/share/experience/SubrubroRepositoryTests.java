package com.openlab.mx.share.experience;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openlab.mx.share.experience.entity.Subrubro;
import com.openlab.mx.share.experience.repository.SubrubroRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nombres fuera de la semilla (SEMILLA de SubrubroSeeder) para no chocar con
 * filas ya sembradas al arrancar el contexto. @Transactional + rollback evita
 * contaminar la H2 compartida entre clases.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubrubroRepositoryTests {

    @Autowired
    private SubrubroRepository repo;

    @Test
    void existsByNameAndCategory() {
        repo.saveAndFlush(Subrubro.builder()
            .nombre("Kayak")
            .categoria("AVENTURA")
            .build());

        assertTrue(repo.existsByNombreAndCategoria("Kayak", "AVENTURA"));
        assertFalse(repo.existsByNombreAndCategoria("Kayak", "CULTURAL"));
    }

    @Test
    void uniqueCategoriaNombre() {
        repo.saveAndFlush(Subrubro.builder()
            .nombre("Buceo")
            .categoria("AVENTURA")
            .build());

        // saveAndFlush pasa por el proxy del repositorio, que SÍ traduce a
        // DataAccessException (un em.flush() crudo lanzaría PersistenceException).
        assertThrows(DataIntegrityViolationException.class, () ->
            repo.saveAndFlush(Subrubro.builder()
                .nombre("Buceo")
                .categoria("AVENTURA")
                .build()));
    }
}
