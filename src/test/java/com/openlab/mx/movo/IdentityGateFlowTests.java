
package com.openlab.mx.movo;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.entity.VerificationStatus;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate de identidad end-to-end con el bean REAL (sin TestIdentityGateConfig):
 * reservar sin verificar redirige a /verificacion con returnUrl; verificado
 * arranca el pago.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.openlab.mx.movo.shared.TestGatewayConfig.class)
class IdentityGateFlowTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DiditVerificationRepository verificationRepository;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    private User usuarioNuevo() {
        String username = "gate-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@x.com")
                .passwordHash("{noop}x")
                .role(com.openlab.mx.movo.user.entity.Role.USER)
                .birthDate(LocalDate.of(1990, 1, 1))
                .build());
    }

    private void marcarVerificado(User user) {
        DiditVerification v = DiditVerification.started(user.getId(), "sess-" + user.getId());
        v.applyDecision(VerificationStatus.VERIFIED, "Approved");
        verificationRepository.save(v);
    }

    private Long instanciaFuturaComo(User creator) {
        marcarVerificado(creator); // el gate de publicar también está activo acá
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                "Gate Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), creator);
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0).getId();
    }

    @Test
    void reserva_sin_verificar_redirige_a_verificacion() throws Exception {
        User creador = usuarioNuevo();
        Long instanceId = instanciaFuturaComo(creador);
        User participante = usuarioNuevo(); // sin verificar

        mockMvc.perform(post("/reservations/" + instanceId)
                        .with(user(participante.getUsername()))
                        .with(csrf())
                        .param("attendees", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/verificacion*"));
    }

    @Test
    void reserva_sin_verificar_xhr_recibe_409_json() throws Exception {
        User creador = usuarioNuevo();
        Long instanceId = instanciaFuturaComo(creador);
        User participante = usuarioNuevo(); // sin verificar

        // Los fetch (XHR) reciben JSON con el código del gate, no un redirect HTML
        // (un fetch sigue el redirect y res.json() recibiría HTML → error de parseo).
        mockMvc.perform(post("/reservations/" + instanceId)
                        .with(user(participante.getUsername()))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("attendees", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_NOT_VERIFIED"));
    }

    @Test
    void reserva_verificada_arranca_el_pago() throws Exception {
        User creador = usuarioNuevo();
        Long instanceId = instanciaFuturaComo(creador);
        User participante = usuarioNuevo();
        marcarVerificado(participante);

        // Verificado pasa el gate y el POST arranca Stripe directo (sin la
        // pantalla intermedia de revisión que existía antes).
        mockMvc.perform(post("/reservations/" + instanceId)
                        .with(user(participante.getUsername()))
                        .with(csrf())
                        .param("attendees", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/__fake_stripe_/checkout/*"));
    }

    @Test
    void publicar_sin_verificar_redirige_a_verificacion() throws Exception {
        User creador = usuarioNuevo();
        creador.setRole(com.openlab.mx.movo.user.entity.Role.CREATOR);
        userRepository.save(creador);

        var imagen = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        var res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/experiences")
                        .file(imagen)
                        .param("name", "Exp sin verificar " + UUID.randomUUID())
                        .param("description", "No debería crearse")
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
                        .with(user(creador.getUsername()).roles("CREATOR"))
                        .with(csrf()))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertEquals(302, res.getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                Objects.requireNonNull(res.getResponse().getRedirectedUrl()).startsWith("/verificacion"),
                "debe redirigir a /verificacion pero fue: " + res.getResponse().getRedirectedUrl());
    }
}