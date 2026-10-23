package com.openlab.mx.share.interest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.Subrubro;
import com.openlab.mx.share.experience.repository.SubrubroRepository;
import com.openlab.mx.share.interest.dto.InterestRubro;
import com.openlab.mx.share.interest.entity.InterestLevel;
import com.openlab.mx.share.interest.repository.UserInterestRepository;
import com.openlab.mx.share.interest.service.InterestService;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterestServiceTests {

    @Autowired
    private InterestService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubrubroRepository subrubroRepository;

    @Autowired
    private UserInterestRepository userInterestRepository;

    private User usuario() {
        String base = "u-svc-" + UUID.randomUUID().toString().substring(0, 8);
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

    /** Subrubros sembrados (los 21 del catálogo) sin depender de nombres. */
    private List<Subrubro> semilla(int cantidad) {
        List<Subrubro> todos = subrubroRepository.findAll();
        assertTrue(todos.size() >= cantidad, "la semilla de subrubros no alcanza");
        return todos.subList(0, cantidad);
    }

    private Subrubro unoDe(String categoria) {
        return subrubroRepository.findByCategoria(categoria).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay subrubros sembrados en " + categoria));
    }

    @Test
    void catalogoDevuelveLosCincoRubrosEnOrdenDelEnum() {
        List<ExperienceCategory> rubros = service.catalogo().stream()
                .map(InterestRubro::rubro)
                .toList();

        assertEquals(List.of(ExperienceCategory.values()), rubros);
    }

    @Test
    void catalogoSoloIncluyeSubrubrosActivos() {
        Subrubro inactivo = subrubroRepository.save(Subrubro.builder()
                .nombre("Sub inactivo " + UUID.randomUUID())
                .categoria("AVENTURA")
                .activo(false)
                .build());

        boolean aparece = service.catalogo().stream()
                .flatMap(r -> r.subrubros().stream())
                .anyMatch(s -> s.getId().equals(inactivo.getId()));

        assertFalse(aparece);
    }

    @Test
    void catalogoIgnoraCategoriasDesconocidas() {
        Subrubro raro = subrubroRepository.save(Subrubro.builder()
                .nombre("Sub raro " + UUID.randomUUID())
                .categoria("GASTRONOMÍA") // con acento: no matchea el enum
                .activo(true)
                .build());

        boolean aparece = service.catalogo().stream()
                .flatMap(r -> r.subrubros().stream())
                .anyMatch(s -> s.getId().equals(raro.getId()));

        assertFalse(aparece);
    }

    @Test
    void rechazaMasDeOchoIntereses() {
        User user = usuario();
        List<Long> nueve = semilla(9).stream().map(Subrubro::getId).toList();

        DomainException ex = assertThrows(DomainException.class,
                () -> service.guardar(user, nueve, Map.of()));

        assertTrue(ex.getMessage().contains("hasta 8"));
    }

    @Test
    void nivelNuloQuedaEnCurioso() {
        User user = usuario();
        Subrubro sub = semilla(1).get(0);

        service.guardar(user, List.of(sub.getId()), Map.of());

        assertEquals(InterestLevel.CURIOSO,
                userInterestRepository.findByUserId(user.getId()).get(0).getLevel());
    }

    @Test
    void guardarReemplazaElSetAnterior() {
        User user = usuario();
        Subrubro a = unoDe("AVENTURA");
        Subrubro b = unoDe("SOCIAL");
        Subrubro c = unoDe("CULTURAL");

        service.guardar(user, List.of(a.getId(), b.getId()), Map.of());
        service.guardar(user, List.of(c.getId()), Map.of());

        var intereses = userInterestRepository.findByUserId(user.getId());
        assertEquals(1, intereses.size());
        assertEquals(c.getId(), intereses.get(0).getSubrubro().getId());
    }

    @Test
    void guardarSinIdsDejaElUsuarioSinIntereses() {
        User user = usuario();
        Subrubro a = unoDe("AVENTURA");
        service.guardar(user, List.of(a.getId()), Map.of());

        service.guardar(user, List.of(), Map.of());

        assertEquals(0, userInterestRepository.countByUserId(user.getId()));
    }

    @Test
    void rechazaSubrubroInexistenteOInactivo() {
        User user = usuario();

        assertThrows(DomainException.class,
                () -> service.guardar(user, List.of(999_999L), Map.of()));

        Subrubro inactivo = subrubroRepository.save(Subrubro.builder()
                .nombre("Sub inactivo " + UUID.randomUUID())
                .categoria("SOCIAL")
                .activo(false)
                .build());
        assertThrows(DomainException.class,
                () -> service.guardar(user, List.of(inactivo.getId()), Map.of()));
    }

    @Test
    void guardarMarcaElFlagDeOnboarding() {
        User user = usuario();
        Subrubro a = unoDe("AVENTURA");

        service.guardar(user, List.of(a.getId()), Map.of());

        assertTrue(userRepository.findById(user.getId()).orElseThrow().isInterestsOnboarded());
    }

    @Test
    void idsDuplicadosNoDuplicanFilas() {
        User user = usuario();
        Subrubro a = unoDe("AVENTURA");

        service.guardar(user, List.of(a.getId(), a.getId()), Map.of());

        assertEquals(1, userInterestRepository.countByUserId(user.getId()));
    }

    @Test
    void interesesOrdenaPorRubroDelEnum() {
        User user = usuario();
        Subrubro social = unoDe("SOCIAL");
        Subrubro aventura = unoDe("AVENTURA");

        // Se guarda SOCIAL primero: el orden de salida lo decide el enum.
        service.guardar(user, List.of(social.getId(), aventura.getId()), Map.of());

        var intereses = service.intereses(user);
        assertEquals("AVENTURA", intereses.get(0).getSubrubro().getCategoria());
        assertEquals("SOCIAL", intereses.get(1).getSubrubro().getCategoria());
    }
}
