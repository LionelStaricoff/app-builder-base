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
import com.openlab.mx.share.user.entity.Role;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(com.openlab.mx.share.shared.TestIdentityGateConfig.class)
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
        vaciar(ExperienceCategory.SOCIAL);
        createExperience("API Exp " + UUID.randomUUID(), ExperienceCategory.SOCIAL);

        mockMvc.perform(get("/feed/api").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").isNumber());
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
        // El rediseño unificó la búsqueda en un solo input (name="city") en el header:
        // el input manual del banner y el desplegable de países ya no existen.
        String html = mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, html.split("name=\"city\"", -1).length - 1,
                "el feed debe renderizar un único buscador");
        assertFalse(html.contains("country-options"),
                "el desplegable de países se eliminó del feed");
    }

    @Test
    @WithMockUser("demo_user")
    void feedConUbicacionMuestraIconoConectado() throws Exception {
        // demo_user tiene ciudad guardada (CDMX): el chip muestra el punto verde.
        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("location-chip")))
                .andExpect(content().string(containsString("bg-emerald-400")))
                .andExpect(content().string(containsString(">CDMX<")));
    }

    @Test
    void feedSinUbicacionNoMuestraIconoConectado() throws Exception {
        String username = crearUsuarioSinCiudad();
        mockMvc.perform(get("/feed").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("bg-emerald-400"))))
                .andExpect(content().string(containsString(">Sin ubicación<")));
    }

    @Test
    @WithMockUser("demo_user")
    void feedBotonMuestraActivadoConUbicacion() throws Exception {
        // Con ubicación guardada, el chip muestra la ciudad y el botón GPS está disponible.
        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"btn-gps\"")))
                .andExpect(content().string(containsString(">CDMX<")));
    }

    @Test
    void feedBotonMuestraActivarSinUbicacion() throws Exception {
        // Sin ubicación guardada, el chip avisa que falta activarla.
        String username = crearUsuarioSinCiudad();
        mockMvc.perform(get("/feed").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"btn-gps\"")))
                .andExpect(content().string(containsString(">Sin ubicación<")));
    }

    @Test
    @WithMockUser("demo_creator")
    void detalleMuestraCiudadYPaisDeParadas() throws Exception {
        Long id = createExperience("Detalle Loc " + UUID.randomUUID(), ExperienceCategory.CULTURAL);
        agregarPrimeraParada(id, "Mirador", "Lima", "Perú", -12.0, -77.0);

        mockMvc.perform(get("/experiences/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lima, Perú")));
    }

    /** Usuario sin ciudad ni coords: representa a quien nunca activó la ubicación. */
    private String crearUsuarioSinCiudad() {
        String base = "u-sinciudad-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                // El feed gatea a quien no pasó por el onboarding de intereses;
                // estos tests verifican ubicación, no onboarding.
                .interestsOnboarded(true)
                .build());
        return base;
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
