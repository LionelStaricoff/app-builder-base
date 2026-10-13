package com.openlab.mx.share.experience;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceStopRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reproducción del reporte: paradas/fechas que no se guardan ni se muestran, edición que pierde datos. */
@SpringBootTest
@AutoConfigureMockMvc
class RoutePersistenceReproTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExperienceStopRepository stopRepository;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    private String fechaFutura() {
        return LocalDate.now().plusDays(30).toString();
    }

    @Test
    @WithMockUser("demo_creator")
    void crearConParadasPersisteParadasYFechas() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        MvcResult result = mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Repro ruta " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "150")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "250.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", fechaFutura())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("stopPlace", "Mirador del Cerro")
                        .param("stopPlace", "Playa Escondida")
                        .param("stopLat", "19.4400")
                        .param("stopLat", "19.4500")
                        .param("stopLng", "-99.1400")
                        .param("stopLng", "-99.1500")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/*"))
                .andReturn();

        Long id = Long.valueOf(result.getResponse().getRedirectedUrl().replace("/experiences/", ""));

        assertEquals(2, stopRepository.findByExperienceIdOrderByPositionAscIdAsc(id).size(),
                "las 2 paradas del POST tienen que estar en la base");
        assertFalse(instanceRepository.findByExperienceIdOrderByDateAsc(id).isEmpty(),
                "tiene que existir al menos una instancia futura");

        mockMvc.perform(get("/experiences/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recorrido")))
                .andExpect(content().string(containsString("Mirador del Cerro")))
                .andExpect(content().string(containsString("Playa Escondida")));
    }

    @Test
    @WithMockUser("demo_creator")
    void editarMuestraDatosCargados() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        String nombre = "Repro edit " + UUID.randomUUID();
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        nombre, "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(14, 30), LocalTime.of(16, 45), null, null)),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());

        mockMvc.perform(get("/experiences/" + experience.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nombre)))
                .andExpect(content().string(containsString("19.4326")))
                .andExpect(content().string(containsString("14:30")))
                .andExpect(content().string(containsString("16:45")));
    }

    @Test
    @WithMockUser("demo_creator")
    void errorDeValidacionConservaParadasEnElForm() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "")
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", fechaFutura())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("stopPlace", "Mirador del Cerro")
                        .param("stopLat", "19.4400")
                        .param("stopLng", "-99.1400")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stops-prefill")))
                .andExpect(content().string(containsString("Mirador del Cerro")));
    }

    @Test
    @WithMockUser("demo_creator")
    void editarSemanalMuestraTodosLosDias() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        "Repro semanal " + UUID.randomUUID(), "Desc", 120, 2, 8,
                        BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.WEEKLY,
                        19.4326, -99.1332,
                        List.of(
                                new ScheduleEntryRequest(null, java.time.DayOfWeek.MONDAY,
                                        LocalTime.of(18, 0), LocalTime.of(20, 0),
                                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(60)),
                                new ScheduleEntryRequest(null, java.time.DayOfWeek.FRIDAY,
                                        LocalTime.of(19, 0), LocalTime.of(21, 0),
                                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(60))),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());

        mockMvc.perform(get("/experiences/" + experience.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("18:00")))
                .andExpect(content().string(containsString("19:00")));
    }
}
