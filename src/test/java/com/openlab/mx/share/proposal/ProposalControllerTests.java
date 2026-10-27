package com.openlab.mx.share.proposal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.repository.SubrubroRepository;
import com.openlab.mx.share.interest.entity.UserInterest;
import com.openlab.mx.share.interest.repository.UserInterestRepository;
import com.openlab.mx.share.proposal.generator.ProposalGenerationException;
import com.openlab.mx.share.proposal.generator.ProposalGenerator;
import com.openlab.mx.share.proposal.entity.ExperienceDraft;
import com.openlab.mx.share.proposal.repository.ExperienceDraftRepository;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Rutas de la pantalla 3 (spec §6): PRG de generar, validación de crear,
 * whitelist de next, borrador inválido ignorado y anon → login.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProposalControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExperienceDraftRepository draftRepository;

    @Autowired
    private SubrubroRepository subrubroRepository;

    @Autowired
    private UserInterestRepository userInterestRepository;

    /** Spy del stub real: los tests normales generan de verdad; el de fallo hace throw. */
    @MockitoSpyBean
    private ProposalGenerator generator;

    private User usuarioNuevo() {
        String base = "u-prop-" + UUID.randomUUID().toString().substring(0, 8);
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

    private void conUnInteres(User user) {
        var subrubro = subrubroRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .findFirst()
                .orElseThrow();
        userInterestRepository.save(UserInterest.builder()
                .user(user).subrubro(subrubro).build());
    }

    @Test
    void getOnboardingPropuestaRenderizaInteresesYGenerador() throws Exception {
        User user = usuarioNuevo();
        conUnInteres(user);

        mockMvc.perform(get("/onboarding/propuesta").with(user(user.getUsername())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("Tu propuesta de experiencia")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("Generar propuesta")));
        // Spec §5: sin borrador no hay nada que editar todavía ("tras propuesta").
        mockMvc.perform(get("/onboarding/propuesta").with(user(user.getUsername())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Editá tu propuesta"))));
    }

    @Test
    void generarHacePrgYCreaFila() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/generar")
                        .with(user(user.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/onboarding/propuesta?next=feed&draft=*"));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                draftRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).size());
    }

    @Test
    void generarConCeroInteresesCreaBorrador() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/generar")
                        .with(user(user.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/onboarding/propuesta?next=feed&draft=*"));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                draftRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).size());
    }

    @Test
    void generarFallidoNoCreaBorrador() throws Exception {
        User user = usuarioNuevo();
        doThrow(new ProposalGenerationException("fallo del proveedor"))
                .when(generator).generate(any());

        mockMvc.perform(post("/onboarding/propuesta/generar")
                        .with(user(user.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/onboarding/propuesta?next=feed"))
                .andExpect(flash().attributeExists("error"));

        org.junit.jupiter.api.Assertions.assertTrue(
                draftRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).isEmpty(),
                "un fallo del generador no persiste borradores a medias");
    }

    @Test
    void crearValidaRedirigeAlFormConFlag() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/crear")
                        .with(user(user.getUsername())).with(csrf())
                        .param("name", "Ruta de tacos")
                        .param("description", "Recorrido por el centro.")
                        .param("category", "GASTRONOMIA")
                        .param("includes", "Agua"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/new?draft=*"));

        User recargado = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(recargado.isProposalOnboarded(),
                "crear experiencia cierra el onboarding de propuesta");
        org.junit.jupiter.api.Assertions.assertEquals(1,
                draftRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).size());
    }

    @Test
    void crearConNombreEnBlancoVuelveALaPantalla() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/crear")
                        .with(user(user.getUsername())).with(csrf())
                        .param("name", " ")
                        .param("description", "Descripción")
                        .param("category", "CULTURAL"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"));

        org.junit.jupiter.api.Assertions.assertTrue(
                draftRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).isEmpty());
    }

    /** El subtipo elegido en la tarjeta viaja al form final en la URL. */
    @Test
    void crearConSubtipoRedirigeAlFormConElSubtipo() throws Exception {
        User user = usuarioNuevo();
        Long subtipo = subrubroRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/onboarding/propuesta/crear")
                        .with(user(user.getUsername())).with(csrf())
                        .param("name", "Con subtipo")
                        .param("description", "Descripción")
                        .param("category", "CULTURAL")
                        .param("subtipo", String.valueOf(subtipo)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(
                        "/experiences/new?draft=*&subtipo=" + subtipo));
    }

    /** Basura en el param (como draft=abc): nunca 400, se ignora y sigue. */
    @Test
    void crearConSubtipoDeBasuraLoIgnora() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/crear")
                        .with(user(user.getUsername())).with(csrf())
                        .param("name", "Subtipo basura")
                        .param("description", "Descripción")
                        .param("category", "CULTURAL")
                        .param("subtipo", "abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/new?draft=*"));
    }

    @Test
    void saltearPoneElFlagYRedirigeAlDestino() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/saltear")
                        .with(user(user.getUsername())).with(csrf()).param("next", "new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/experiences/new"));

        mockMvc.perform(post("/onboarding/propuesta/saltear")
                        .with(user(user.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));

        org.junit.jupiter.api.Assertions.assertTrue(
                userRepository.findById(user.getId()).orElseThrow().isProposalOnboarded());
    }

    @Test
    void nextMaliciosoCaeAlFeed() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(post("/onboarding/propuesta/saltear")
                        .with(user(user.getUsername())).with(csrf())
                        .param("next", "//evil.com/robar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
    }

    @Test
    void draftNoNumericoSeIgnora() throws Exception {
        User user = usuarioNuevo();

        mockMvc.perform(get("/onboarding/propuesta")
                        .with(user(user.getUsername())).param("draft", "abc"))
                .andExpect(status().isOk());
    }

    /**
     * Spec §5: los campos del form salen prellenados del borrador (?draft= o
     * el ultimo propio). Sin esto, "Crear experiencia" falla en el navegador:
     * el select de categoría queda vacío y el `required` ni siquiera manda el
     * POST (lo cubrió el e2e antes que la suite Java).
     */
    @Test
    void getPrecargaElFormDesdeElBorrador() throws Exception {
        User user = usuarioNuevo();
        ExperienceDraft draft = draftRepository.save(ExperienceDraft.builder()
                .user(user)
                .name("Nombre borrador unico")
                .description("Descripcion del borrador para el form")
                .category(ExperienceCategory.CULTURAL)
                .build());

        mockMvc.perform(get("/onboarding/propuesta")
                        .with(user(user.getUsername()))
                        .param("draft", String.valueOf(draft.getId())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString(
                                "value=\"Nombre borrador unico\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString(
                                "Descripcion del borrador para el form")));
        // Con borrador, el form de edición sí se muestra.
        mockMvc.perform(get("/onboarding/propuesta")
                        .with(user(user.getUsername()))
                        .param("draft", String.valueOf(draft.getId())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("Editá tu propuesta")));
    }

    @Test
    void anonimoSinSesionRedirigeALogin() throws Exception {
        mockMvc.perform(get("/onboarding/propuesta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
