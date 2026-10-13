package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.repository.ExperienceStopRepository;
import com.openlab.mx.share.experience.entity.ExperienceStop;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeedAndFavoritesControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private ExperienceRepository experienceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExperienceStopRepository stopRepository;

    private User creator() {
        return userRepository.findByUsername("demo_creator").orElseThrow();
    }

    private Long createExperience() {
        return createExperience("Feed Exp " + UUID.randomUUID(), ExperienceCategory.CULTURAL);
    }

    private Long createExperience(String name, ExperienceCategory category) {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                name, "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                category, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.of(2026, 12, 1), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image)), creator()).getId();
    }

    /** Deja la categoría vacía para poder assertear el empty-state de forma determinística. */
    private void vaciar(ExperienceCategory category) {
        var activas = experienceRepository.findByStatusAndCategory(ExperienceStatus.ACTIVE, category);
        activas.forEach(e -> e.setStatus(ExperienceStatus.CANCELLED));
        experienceRepository.saveAll(activas);
    }

    /** Cancela TODAS las activas: el filtro por país geocodifica paradas faltantes (red). */
    private void vaciarTodo() {
        var activas = experienceRepository.findByStatus(ExperienceStatus.ACTIVE);
        activas.forEach(e -> e.setStatus(ExperienceStatus.CANCELLED));
        experienceRepository.saveAll(activas);
    }

    private void agregarPrimeraParada(Long expId, String place, String city, String country,
                                      double lat, double lng) {
        var exp = experienceRepository.findById(expId).orElseThrow();
        stopRepository.save(ExperienceStop.builder()
                .experience(exp).position(0).placeName(place).city(city).country(country)
                .lat(lat).lng(lng)
                .build());
    }

    // ---------- Feed ----------

    @Test
    void feedSinSesionRedirigeALogin() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser("demo_user")
    void feedRenderiza200() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser("demo_user")
    void feedRenderizaElNombreDeLaExperienciaEnLaTarjeta() throws Exception {
        vaciar(ExperienceCategory.SOCIAL);
        String nombre = "Social Exp " + UUID.randomUUID();
        createExperience(nombre, ExperienceCategory.SOCIAL);

        mockMvc.perform(get("/feed").param("category", "SOCIAL"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(nombre)));
    }

    @Test
    @WithMockUser("demo_user")
    void feedVacioRenderizaEmptyState() throws Exception {
        vaciar(ExperienceCategory.AVENTURA);

        mockMvc.perform(get("/feed").param("category", "AVENTURA"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Todavía no hay experiencias por acá")));
    }

    @Test
    @WithMockUser("demo_user")
    void feedApiDevuelveJsonConUnaTarjeta() throws Exception {
        mockMvc.perform(get("/feed/api").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    @WithMockUser("demo_user")
    void feedApiPaginaAllaDelFinalDevuelveVacia() throws Exception {
        mockMvc.perform(get("/feed/api").param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @WithMockUser("demo_user")
    void feedMuestraElLoaderSvgDeMasTarjetas() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("feed-loading")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("loader-svg")));
    }

    @Test
    @WithMockUser("demo_user")
    void feedBuscadorUnicoSeRenderiza() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("location-manual-input")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("country-options")));
    }

    @Test
    @WithMockUser("demo_user")
    void feedFiltraPorPaisDePrimeraParada() throws Exception {
        vaciarTodo();
        String peruano = "Perú Exp " + UUID.randomUUID();
        String mexicano = "México Exp " + UUID.randomUUID();
        Long per = createExperience(peruano, ExperienceCategory.CULTURAL);
        Long mex = createExperience(mexicano, ExperienceCategory.CULTURAL);
        agregarPrimeraParada(per, "Mirador", "Lima", "Perú", -12.0, -77.0);
        agregarPrimeraParada(mex, "Zócalo", "CDMX", "México", 19.43, -99.13);

        mockMvc.perform(get("/feed").param("country", "Perú"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(peruano)))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(mexicano))));
    }

    @Test
    @WithMockUser("demo_user")
    void feedApiAceptaElFiltroDePais() throws Exception {
        vaciarTodo(); // sin activas sin paradas: el fallback no debe geocodificar en tests
        mockMvc.perform(get("/feed/api").param("page", "0").param("country", "Argentina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ---------- Favoritos ----------

    @Test
    void favoritosSinSesionRedirigeALogin() throws Exception {
        mockMvc.perform(multipart("/favorites/1/add").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser("demo_user")
    void addFavoritoRedirigeConPrg() throws Exception {
        Long id = createExperience();

        mockMvc.perform(multipart("/favorites/" + id + "/add").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/favorites"));
    }

    @Test
    @WithMockUser("demo_user")
    void listaDeFavoritosMuestraLaExperiencia() throws Exception {
        Long id = createExperience();
        mockMvc.perform(multipart("/favorites/" + id + "/add").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/favorites"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Feed Exp")));
    }

    @Test
    @WithMockUser("demo_user")
    void removeFavoritoEsPrg() throws Exception {
        Long id = createExperience();
        mockMvc.perform(multipart("/favorites/" + id + "/add").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(multipart("/favorites/" + id + "/remove").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/favorites"));
    }
}
