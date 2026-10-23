package com.openlab.mx.share.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.entity.Subrubro;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.repository.SubrubroRepository;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExperienceSubrubroTests {

    @Autowired
    private ExperienceRepository expRepo;

    @Autowired
    private SubrubroRepository subRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private EntityManager em;

    /**
     * Experience exige creator (optional=false) y price/category/recurrenceType/
     * meetingLat/meetingLng son NOT NULL en el esquema. Un builder parcial no
     * persiste: este helper arma una experiencia válida y completa.
     */
    private Experience experience(ExperienceCategory category, Subrubro subrubro) {
        var creator = userRepo.save(User.builder()
            .username("creator_subrubro_test")
            .email("creator_subrubro_test@x.com")
            .passwordHash("{noop}Clave1234!")
            .birthDate(LocalDate.of(1995, 1, 1))
            .role(Role.USER)
            .build());
        return Experience.builder()
            .creator(creator)
            .name("Experiencia de prueba")
            .description("Descripción de prueba")
            .durationMinutes(60)
            .minCapacity(1)
            .maxCapacity(8)
            .price(new BigDecimal("125.00"))
            .category(category)
            .recurrenceType(RecurrenceType.UNIQUE)
            .meetingLat(19.4326)
            .meetingLng(-99.1332)
            .subrubro(subrubro)
            .build();
    }

    @Test
    void experienciaConSubrubro() {
        var kayak = subRepo.save(Subrubro.builder()
            .nombre("Kayak de mar")
            .categoria("AVENTURA")
            .build());

        var saved = expRepo.saveAndFlush(experience(ExperienceCategory.AVENTURA, kayak));

        em.clear(); // fuerza releer de la DB y resolver el LAZY de verdad
        var found = expRepo.findById(saved.getId()).orElseThrow();
        assertNotNull(found.getSubrubro());
        assertEquals("Kayak de mar", found.getSubrubro().getNombre());
    }

    @Test
    void experienciaSinSubrubro() {
        var saved = expRepo.saveAndFlush(experience(ExperienceCategory.CULTURAL, null));

        em.clear();
        var found = expRepo.findById(saved.getId()).orElseThrow();
        assertNull(found.getSubrubro());
    }
}
