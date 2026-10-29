package com.openlab.mx.movo.interest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openlab.mx.movo.experience.entity.Subrubro;
import com.openlab.mx.movo.experience.repository.SubrubroRepository;
import com.openlab.mx.movo.interest.entity.InterestLevel;
import com.openlab.mx.movo.interest.entity.UserInterest;
import com.openlab.mx.movo.interest.repository.UserInterestRepository;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Nombres de subrubro con UUID para no chocar con la semilla del
 * SubrubroSeeder (índice único categoria+nombre). @Transactional + rollback
 * evita contaminar la H2 compartida entre clases.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserInterestRepositoryTests {

    @Autowired
    private UserInterestRepository repo;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubrubroRepository subrubroRepository;

    @PersistenceContext
    private EntityManager em;

    private User usuario() {
        String base = "u-interest-" + UUID.randomUUID().toString().substring(0, 8);
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

    private Subrubro subrubro(String categoria) {
        return subrubroRepository.save(Subrubro.builder()
                .nombre("Sub " + UUID.randomUUID())
                .categoria(categoria)
                .activo(true)
                .build());
    }

    private UserInterest interes(User user, Subrubro subrubro, InterestLevel level) {
        return UserInterest.builder().user(user).subrubro(subrubro).level(level).build();
    }

    @Test
    void noPermiteRepetirElMismoSubrubroParaElMismoUsuario() {
        User user = usuario();
        Subrubro sub = subrubro("AVENTURA");

        repo.saveAndFlush(interes(user, sub, InterestLevel.CURIOSO));

        assertThrows(DataIntegrityViolationException.class, () ->
                repo.saveAndFlush(interes(user, sub, InterestLevel.EXPERTO)));
    }

    @Test
    void leeElSubrubroEagerSinProxy() {
        User user = usuario();
        Subrubro sub = subrubro("AVENTURA");
        repo.saveAndFlush(interes(user, sub, InterestLevel.EXPERTO));
        em.clear();

        var intereses = repo.findByUserId(user.getId());

        assertEquals(1, intereses.size());
        // Sin @EntityGraph esto sería un proxy: en un test @Transactional el
        // lazy se resolvería igual y el test no probaría nada.
        assertTrue(Hibernate.isInitialized(intereses.get(0).getSubrubro()));
        assertEquals(sub.getNombre(), intereses.get(0).getSubrubro().getNombre());
        assertEquals(InterestLevel.EXPERTO, intereses.get(0).getLevel());
    }

    @Test
    void deleteByUserIdBorraSoloLosDelUsuario() {
        User uno = usuario();
        User otro = usuario();
        Subrubro subA = subrubro("AVENTURA");
        Subrubro subB = subrubro("SOCIAL");

        repo.saveAndFlush(interes(uno, subA, InterestLevel.CURIOSO));
        repo.saveAndFlush(interes(otro, subB, InterestLevel.CURIOSO));

        repo.deleteByUserId(uno.getId());
        em.flush();
        em.clear();

        assertFalse(repo.existsByUserIdAndSubrubroId(uno.getId(), subA.getId()));
        assertTrue(repo.existsByUserIdAndSubrubroId(otro.getId(), subB.getId()));
        assertEquals(1, repo.countByUserId(otro.getId()));
    }
}
