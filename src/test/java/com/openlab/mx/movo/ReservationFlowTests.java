
package com.openlab.mx.movo;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.shared.TestGatewayConfig;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import com.openlab.mx.movo.shared.TestIdentityGateConfig;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestGatewayConfig.class, TestIdentityGateConfig.class})
class ReservationFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.openlab.mx.movo.reservation.repository.ReservationRepository reservationRepository;

    private Long fechaFutura() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                "Checkout Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null),
                userRepository.findByUsername("demo_creator").orElseThrow());
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0).getId();
    }

    @Test
    @WithMockUser("demo_user")
    void flujoReservaYPagoDirectoAStripe() throws Exception {
        Long instanceId = fechaFutura();

        // Sin pantalla intermedia: el POST de la fecha ya crea la sesión de
        // Stripe y redirige a la página hosted.
        String[] redirect = new String[1];
        mockMvc.perform(post("/reservations/" + instanceId).with(csrf())
                        .param("attendees", "2"))
                .andExpect(status().is3xxRedirection())
                .andDo(r -> redirect[0] = r.getResponse().getRedirectedUrl());
        org.junit.jupiter.api.Assertions.assertNotNull(redirect[0]);
        org.junit.jupiter.api.Assertions.assertTrue(
                redirect[0].contains("/__fake_stripe_/checkout/"),
                "tiene que ir derecho a Stripe, no a una revisión intermedia");

        Long userId = userRepository.findByUsername("demo_user").orElseThrow().getId();
        var reservation = reservationRepository.findFirstByInstanceIdAndUserIdAndStatus(
                instanceId, userId,
                com.openlab.mx.movo.reservation.entity.ReservationStatus.PENDIENTE)
                .orElseThrow();
        String sessionId = paymentRepository.findByReservationId(reservation.getId())
                .orElseThrow().getStripeSessionId();

        mockMvc.perform(get("/pago/exito").param("session_id", sessionId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/confirmation"));

        mockMvc.perform(get("/confirmation"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PAGASTE")));
    }

    @Test
    void sinSesionLosFluujosRedirigenALogin() throws Exception {
        mockMvc.perform(post("/reservations/1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/confirmation"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---------- instant book: sin confirmación manual (spec §2.1) ----------

    @Test
    void confirmacionManualYaNoExiste() throws Exception {
        mockMvc.perform(post("/reservations/1/confirm")
                        .with(user("demo_creator").roles("CREATOR")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser("demo_user")
    void misReservasNoMuestraLaColaDeConfirmacion() throws Exception {
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Esperando tu confirmación"))));
    }
}