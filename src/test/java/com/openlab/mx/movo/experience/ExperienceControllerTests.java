
package com.openlab.mx.movo.experience;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.proposal.entity.ExperienceDraft;
import com.openlab.mx.movo.proposal.repository.ExperienceDraftRepository;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(com.openlab.mx.movo.shared.TestIdentityGateConfig.class)
class ExperienceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

    @Autowired
    private ExperienceDraftRepository draftRepository;

    @Autowired
    private com.openlab.mx.movo.experience.repository.SubrubroRepository subrubros;

    private User creator() {
        return userRepository.findByUsername("demo_creator").orElseThrow();
    }

    private Long createExperience() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(formRequest("Existente " + UUID.randomUUID(), List.of(image)),
                creator()).getId();
    }

    private ExperienceFormRequest formRequest(String name, List<org.springframework.web.multipart.MultipartFile> images) {
        return new ExperienceFormRequest(
                name, "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.of(2026, 12, 1), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                images, null, null);
    }

    @Test
    void sinSesionRedirigeALogin() throws Exception {
        mockMvc.perform(get("/experiences/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void creaExperienciaConMultipartYRedirigeAlDetalle() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Tour nuevo " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", "2026-12-01")
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/*"));
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void validacionInvalidaVuelveAlFormConErrores() throws Exception {
        mockMvc.perform(multipart("/experiences")
                        .param("name", "")
                        .param("description", "")
                        .param("durationMinutes", "0")
                        .param("minCapacity", "0")
                        .param("maxCapacity", "0")
                        .param("price", "190")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4")
                        .param("meetingLng", "-99.1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("error")));
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void misExperienciasRenderiza() throws Exception {
        mockMvc.perform(get("/my-experiences"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mis Experiencias")));
    }

    @Test
    @WithMockUser("demo_user")
    void experienciaInexistenteRedirigeConMensajeFlash() throws Exception {
        mockMvc.perform(get("/experiences/99999999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void duenoveFormDeEdicion() throws Exception {
        Long id = createExperience();

        mockMvc.perform(get("/experiences/" + id + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Existente")));
    }

    @Test
    @WithMockUser("demo_user")
    void otroUsuarioNoPuedeEditarRecibe403() throws Exception {
        Long id = createExperience();

        mockMvc.perform(get("/experiences/" + id + "/edit"))
                .andExpect(status().isForbidden());
    }

    // ── Prefill desde un borrador de propuesta (?draft={id}) ──────────────

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void draftPropioPrecargaElContenido() throws Exception {
        var draft = draftRepository.save(ExperienceDraft.builder()
                .user(creator())
                .name("Nombre borrador " + UUID.randomUUID())
                .description("Descripción del borrador lista para editar")
                .category(ExperienceCategory.GASTRONOMIA)
                .includes("Entrada al recorrido")
                .bring("Calzado cómodo")
                .build());

        mockMvc.perform(get("/experiences/new").param("draft", String.valueOf(draft.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"" + draft.getName() + "\"")))
                // Thymeleaf escapa los acentos como entidades (&oacute;), así que
                // el match va contra el trozo sin acentos: sigue siendo exclusivo
                // de la descripción prellenada.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "del borrador lista para editar")));
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void draftAjenoIgnoraElPrefill() throws Exception {
        var ajeno = draftRepository.save(ExperienceDraft.builder()
                .user(userRepository.findByUsername("demo_user").orElseThrow())
                .name("Borrador ajeno secreto")
                .description("No debería verse en este form")
                .category(ExperienceCategory.CULTURAL)
                .build());

        mockMvc.perform(get("/experiences/new").param("draft", String.valueOf(ajeno.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Borrador ajeno secreto"))));
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void draftNoNumericoNoRompeElForm() throws Exception {
        mockMvc.perform(get("/experiences/new").param("draft", "abc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void linkCrearConIaVisible() throws Exception {
        mockMvc.perform(get("/experiences/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/onboarding/propuesta?next=new\"")));
    }

    /**
     * El subtipo elegido en la tarjeta de propuesta llega en la URL y el form
     * lo pone en un hidden para que el POST de crear lo persista.
     */
    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void subtipoSePrefillEnElFormComoOculto() throws Exception {
        Long subtipo = subrubros.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(get("/experiences/new").param("subtipo", String.valueOf(subtipo)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"subtipo\" value=\"" + subtipo + "\"")));
    }

    /** El param basura (como draft=abc) se ignora: 200, nunca 400. */
    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void subtipoDeBasuraNoRompeElForm() throws Exception {
        mockMvc.perform(get("/experiences/new").param("subtipo", "abc"))
                .andExpect(status().isOk());
    }

    /** El subtipo válido termina como FK de la experiencia al crear. */
    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void crearConSubtipoPersisteElFk() throws Exception {
        var subtipo = subrubros.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .filter(s -> "GASTRONOMIA".equals(s.getCategoria()))
                .findFirst().orElseThrow();
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        var resultado = mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Con subtipo " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("category", "GASTRONOMIA")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", "2026-12-01")
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("subtipo", String.valueOf(subtipo.getId()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/*"))
                .andReturn();

        String location = resultado.getResponse().getRedirectedUrl();
        Long id = Long.valueOf(location.replaceAll("[^0-9]", ""));
        // getSubrubro() vuelve como proxy lazy (Subrubro no tiene equals por
        // identidad): el contrato es el id de la FK, no la instancia.
        var guardado = experienceRepository.findById(id).orElseThrow().getSubrubro();
        org.junit.jupiter.api.Assertions.assertNotNull(guardado);
        org.junit.jupiter.api.Assertions.assertEquals(subtipo.getId(), guardado.getId());
    }

    /** Un subtipo de OTRA categoría no se asigna (la categoría manda). */
    @Test
    @WithMockUser(username = "demo_creator", roles = {"CREATOR"})
    void crearConSubtipoDeOtraCategoriaNoSeAsigna() throws Exception {
        var ajeno = subrubros.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .filter(s -> !"GASTRONOMIA".equals(s.getCategoria()))
                .findFirst().orElseThrow();
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        var resultado = mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Subtipo ajeno " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("category", "GASTRONOMIA")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", "2026-12-01")
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("subtipo", String.valueOf(ajeno.getId()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = resultado.getResponse().getRedirectedUrl();
        Long id = Long.valueOf(location.replaceAll("[^0-9]", ""));
        org.junit.jupiter.api.Assertions.assertNull(
                experienceRepository.findById(id).orElseThrow().getSubrubro());
    }
}