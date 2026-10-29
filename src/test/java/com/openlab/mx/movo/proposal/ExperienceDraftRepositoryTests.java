package com.openlab.mx.movo.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.proposal.entity.ExperienceDraft;
import com.openlab.mx.movo.proposal.repository.ExperienceDraftRepository;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Migración V9 + entity: la suite entera corre con ddl-auto=validate, así que
 * si la tabla o el mapeo no cierran, este test (y los otros 378) revientan.
 */
@SpringBootTest
class ExperienceDraftRepositoryTests {

    @Autowired
    private ExperienceDraftRepository repository;

    @Autowired
    private UserRepository userRepository;

    private User usuarioNuevo() {
        String base = "u-draft-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
    }

    private ExperienceDraft draft(User user, String name) {
        return ExperienceDraft.builder()
                .user(user)
                .name(name)
                .description("Descripción de " + name)
                .category(ExperienceCategory.GASTRONOMIA)
                .includes("Entrada")
                .bring("Agua")
                .build();
    }

    @Test
    void guardaYDevuelveElUltimoBorradorDelUsuario() {
        User user = usuarioNuevo();

        ExperienceDraft viejo = draft(user, "Viejo");
        viejo.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(1));
        repository.save(viejo);
        repository.save(draft(user, "Nuevo"));

        assertEquals("Nuevo",
                repository.findTopByUserIdOrderByUpdatedAtDesc(user.getId())
                        .orElseThrow()
                        .getName());
        assertEquals(2, repository.findByUserIdOrderByUpdatedAtDesc(user.getId()).size());
    }
}
