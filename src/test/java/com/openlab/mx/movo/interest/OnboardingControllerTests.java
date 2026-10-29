package com.openlab.mx.movo.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.openlab.mx.movo.experience.entity.Subrubro;
import com.openlab.mx.movo.experience.repository.SubrubroRepository;
import com.openlab.mx.movo.interest.entity.InterestLevel;
import com.openlab.mx.movo.interest.entity.UserInterest;
import com.openlab.mx.movo.interest.repository.UserInterestRepository;
import com.openlab.mx.movo.interest.service.InterestService;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gate del feed + los pasos del onboarding de intereses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubrubroRepository subrubroRepository;

    @Autowired
    private UserInterestRepository userInterestRepository;

    @Autowired
    private InterestService interestService;

    /** Usuario nuevo: interestsOnboarded es false por default. */
    private User usuarioNuevo() {
        String base = "u-onb-" + UUID.randomUUID().toString().substring(0, 8);
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

    private List<Subrubro> subrubrosActivos(int cuantos) {
        return subrubroRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .limit(cuantos)
                .toList();
    }

    // --- gate -------------------------------------------------------------

    @Test
    void feedGateaAlUsuarioNuevo() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/feed").with(user(nuevo.getUsername())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding/intereses"));
    }

    @Test
    void feedNoGateaAlUsuarioQueYaVioElOnboarding() throws Exception {
        mockMvc.perform(get("/feed").with(user("demo_user")))
                .andExpect(status().isOk());
    }

    @Test
    void feedCardsNoGatea() throws Exception {
        // El scroll infinito no puede recibir el redirect del onboarding:
        // inyectaría la pantalla de intereses dentro de la lista de cards.
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/feed/cards").param("page", "0").with(user(nuevo.getUsername())))
                .andExpect(status().isOk());
    }

    // --- paso 1 -----------------------------------------------------------

    @Test
    void pasoUnoMuestraElCatalogoDeRubros() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/onboarding/intereses").with(user(nuevo.getUsername())))
                .andExpect(status().isOk())
                .andExpect(view().name("interest/onboarding"))
                .andExpect(model().attribute("rubros", hasSize(5)))
                .andExpect(model().attribute("max", InterestService.MAX_INTERESES))
                .andExpect(model().attribute("next", "feed"))
                .andExpect(model().attribute("seleccionados", hasSize(0)));
    }

    @Test
    void pasoUnoPrecargaLoQueElUsuarioYaTenia() throws Exception {
        User nuevo = usuarioNuevo();
        long subrubroId = subrubrosActivos(1).get(0).getId();
        interestService.guardar(nuevo, List.of(subrubroId), Map.of(subrubroId, InterestLevel.AFICIONADO));

        mockMvc.perform(get("/onboarding/intereses").param("next", "settings")
                        .with(user(nuevo.getUsername())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("seleccionados", hasSize(1)))
                .andExpect(model().attribute("next", "settings"));
    }

    // --- paso 2 -----------------------------------------------------------

    @Test
    void pasoDosListaLosElegidosConSuNivel() throws Exception {
        User nuevo = usuarioNuevo();
        List<Subrubro> elegidos = subrubrosActivos(2);

        var request = post("/onboarding/intereses").with(csrf()).with(user(nuevo.getUsername()));
        for (Subrubro s : elegidos) {
            request.param("subrubroIds", s.getId().toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("interest/levels"))
                .andExpect(model().attribute("elegidos", hasSize(2)))
                .andExpect(model().attribute("niveles", aMapWithSize(2)));
    }

    @Test
    void pasoDosSinSeleccionVuelveAlPasoUnoConError() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(post("/onboarding/intereses").with(csrf()).with(user(nuevo.getUsername())))
                .andExpect(status().isOk())
                .andExpect(view().name("interest/onboarding"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void pasoDosRechazaMasDeOchoIntereses() throws Exception {
        User nuevo = usuarioNuevo();
        List<Subrubro> activos = subrubroRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .limit(InterestService.MAX_INTERESES + 1)
                .toList();

        var request = post("/onboarding/intereses").with(csrf()).with(user(nuevo.getUsername()));
        for (Subrubro s : activos) {
            request.param("subrubroIds", s.getId().toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("interest/onboarding"))
                .andExpect(model().attributeExists("error"));
    }

    // --- guardar ----------------------------------------------------------

    @Test
    void guardarNivelesRedirigeAlFeedYPersiste() throws Exception {
        User nuevo = usuarioNuevo();
        Subrubro subrubro = subrubrosActivos(1).get(0);

        mockMvc.perform(post("/onboarding/intereses/niveles").with(csrf()).with(user(nuevo.getUsername()))
                        .param("next", "feed")
                        .param("subrubroIds", subrubro.getId().toString())
                        .param("levels[" + subrubro.getId() + "]", "EXPERTO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));

        List<UserInterest> guardados = userInterestRepository.findByUserId(nuevo.getId());
        assertThat(guardados).hasSize(1);
        assertThat(guardados.get(0).getLevel()).isEqualTo(InterestLevel.EXPERTO);
        assertThat(userRepository.findById(nuevo.getId()).orElseThrow().isInterestsOnboarded()).isTrue();
    }

    @Test
    void guardarRespetaElDestinoSettings() throws Exception {
        User nuevo = usuarioNuevo();
        Subrubro subrubro = subrubrosActivos(1).get(0);

        mockMvc.perform(post("/onboarding/intereses/niveles").with(csrf()).with(user(nuevo.getUsername()))
                        .param("next", "settings")
                        .param("subrubroIds", subrubro.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));
    }

    @Test
    void nextInvalidoNoSaleDelSitio() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(post("/onboarding/intereses/saltear").with(csrf()).with(user(nuevo.getUsername()))
                        .param("next", "https://evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
    }

    @Test
    void saltearDejaAlUsuarioSinInteresesPeroOnboarded() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(post("/onboarding/intereses/saltear").with(csrf()).with(user(nuevo.getUsername())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));

        assertThat(userInterestRepository.findByUserId(nuevo.getId())).isEmpty();
        assertThat(userRepository.findById(nuevo.getId()).orElseThrow().isInterestsOnboarded()).isTrue();
    }

    @Test
    void elGateDejaDeAplicarDespuesDeSaltear() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(post("/onboarding/intereses/saltear").with(csrf()).with(user(nuevo.getUsername())))
                .andExpect(redirectedUrl("/feed"));

        // El gate de intereses ya no aplica: si siguiera activo, la URL sería
        // /onboarding/intereses. Ahora pisa el gate 2 (pantalla de propuesta),
        // que es el siguiente paso del onboarding para un usuario nuevo.
        mockMvc.perform(get("/feed").with(user(nuevo.getUsername())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding/propuesta"));
    }
}
