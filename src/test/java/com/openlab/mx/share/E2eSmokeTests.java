package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.TestGatewayConfig;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestGatewayConfig.class, com.openlab.mx.share.shared.TestIdentityGateConfig.class})
class E2eSmokeTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void smokeDeFeedASettings() throws Exception {
        // 1. el creador entra al feed
        mockMvc.perform(get("/feed").with(user("demo_creator")))
                .andExpect(status().isOk());

        // 2. crea una experiencia que aparece en el feed
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        String nombre = "Smoke Exp " + UUID.randomUUID();
        Experience experience = experienceService.create(new ExperienceFormRequest(
                nombre, "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.GASTRONOMIA, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        Long instanceId = instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId())
                .get(0).getId();

        mockMvc.perform(get("/feed").param("category", "GASTRONOMIA").with(user("demo_user")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nombre)));

        // 3. demo_user reserva y paga
        mockMvc.perform(post("/reservations/" + instanceId).with(user("demo_user")).with(csrf())
                        .param("attendees", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout/" + instanceId + "?attendees=1"));

        // 3. demo_user reserva: POST redirige a la página de Stripe (fake) y el
        //    retorno /pago/exito revalida la sesión (spec §6) antes de confirmar.
        mockMvc.perform(post("/checkout/" + instanceId).with(user("demo_user")).with(csrf())
                        .param("attendees", "1"))
                .andExpect(status().is3xxRedirection());

        Long userId = userRepository.findByUsername("demo_user").orElseThrow().getId();
        Reservation reservationParaPagar = reservationRepository
                .findFirstByInstanceIdAndUserIdAndStatus(instanceId, userId,
                        ReservationStatus.PENDIENTE).orElseThrow();
        String sessionId = paymentRepository.findByReservationId(
                reservationParaPagar.getId()).orElseThrow().getStripeSessionId();
        mockMvc.perform(get("/pago/exito").param("session_id", sessionId)
                        .with(user("demo_user")))
                .andExpect(status().is3xxRedirection());

        // 4. el creador confirma
        Long reservationId = reservationParaPagar.getId();
        mockMvc.perform(post("/reservations/" + reservationId + "/confirm")
                        .with(user("demo_creator")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 5. la experiencia termina (lo haría el job) y demo_user evalúa
        ExperienceInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        instance.setStatus(InstanceStatus.COMPLETED);
        instanceRepository.save(instance);

        mockMvc.perform(post("/instances/" + instanceId + "/rate").with(user("demo_user")).with(csrf())
                        .param("stars", "5").param("text", "Todo perfecto"))
                .andExpect(status().is3xxRedirection());

        // 6. escrow liberado con comisión del 5%
        var payment = paymentRepository.findByReservationId(reservationId).orElseThrow();
        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        assertEquals(0, payment.getCommission().compareTo(
                payment.getAmount().multiply(new BigDecimal("0.05"))), "comisión 5%");
        assertEquals(0, payment.getPayout().compareTo(
                payment.getAmount().subtract(payment.getCommission())));

        // 7. settings muestra los SP
        mockMvc.perform(get("/settings").with(user("demo_user")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SP")));
    }
}
