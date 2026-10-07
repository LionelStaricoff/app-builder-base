package com.openlab.mx.share.experience;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExperienceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

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
                images);
    }

    @Test
    void sinSesionRedirigeALogin() throws Exception {
        mockMvc.perform(get("/experiences/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser("demo_creator")
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
    @WithMockUser("demo_creator")
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
    @WithMockUser("demo_creator")
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
}
