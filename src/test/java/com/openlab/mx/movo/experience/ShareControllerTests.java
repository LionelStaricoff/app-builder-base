
package com.openlab.mx.movo.experience;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.user.repository.UserRepository;
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

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Botón "Compartir viaje": el controller responde url canónica + título + descripción. */
import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(com.openlab.mx.movo.shared.TestIdentityGateConfig.class)
class ShareControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private UserRepository userRepository;

    private Long crearExperiencia(String nombre) {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                        nombre, "Una descripción compartible", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.of(2026, 12, 1), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                        List.of(image), null, null),
                userRepository.findByUsername("demo_creator").orElseThrow()).getId();
    }

    @Test
    @WithMockUser("demo_user")
    void shareMetaDevuelveUrlTituloYDescripcion() throws Exception {
        String nombre = "Share Exp " + UUID.randomUUID();
        Long id = crearExperiencia(nombre);

        mockMvc.perform(get("/share/" + id + "/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(endsWith("/experiences/" + id)))
                .andExpect(jsonPath("$.title").value(nombre))
                .andExpect(jsonPath("$.description").value(not(org.hamcrest.Matchers.emptyString())));
    }

    @Test
    @WithMockUser("demo_user")
    void shareMetaDeExperienciaInexistenteEs404() throws Exception {
        mockMvc.perform(get("/share/999999/meta"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser("demo_user")
    void feedTieneElBotonDeCompartir() throws Exception {
        // La card solo se renderiza si hay experiencias activas: creo una.
        crearExperiencia("Con botón compartir " + UUID.randomUUID());

        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-share-experience")));
    }
}