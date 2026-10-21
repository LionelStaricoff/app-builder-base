
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
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.ReservationService;
import com.openlab.mx.share.user.entity.User;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({com.openlab.mx.share.shared.TestGatewayConfig.class,
        com.openlab.mx.share.shared.TestIdentityGateConfig.class})
class CancellationFlowTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;

    private User usuario(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Long fechaFutura() {
        return instanceRepository.findByExperienceIdOrderByDateAsc(crearExperiencia().getId())
                .get(0).getId();
    }

    /** Fecha que empieza dentro de 24h → tramo de 50%. */
    private Long fechaEnMenosDe24h() {
        LocalDateTime inicio = LocalDateTime.now().plusHours(2);
        ExperienceInstance instance = ExperienceInstance.builder()
                .experience(crearExperiencia())
                .date(inicio.toLocalDate())
                .startTime(inicio.toLocalTime())
                .endTime(inicio.toLocalTime().plusHours(1))
                .status(InstanceStatus.SCHEDULED)
                .build();
        return instanceRepository.save(instance).getId();
    }

    private Experience crearExperiencia() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                "Cancel Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image)), usuario("demo_creator"));
    }

    @Test
    void participanteCancelaSuReservaConReembolso() throws Exception {
        Long instanceId = fechaFutura();
        // Flujo Stripe nuevo: checkout crea PENDIENTE + payment, exito lo pone HELD.
        mockMvc.perform(post("/checkout/" + instanceId)
                        .with(user("demo_user")).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection());
        Reservation reserva = reservationRepository
                .findFirstByInstanceIdAndUserIdAndStatus(
                        instanceId, usuario("demo_user").getId(), ReservationStatus.PENDIENTE)
                .orElseThrow();
        String sessionId = paymentRepository.findByReservationId(reserva.getId())
                .orElseThrow().getStripeSessionId();
        mockMvc.perform(get("/pago/exito").param("session_id", sessionId)
                        .with(user("demo_user")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/reservations/" + reserva.getId() + "/cancel")
                        .with(user("demo_user")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("ok", containsString("90%")));

        Reservation guardada = reservationRepository.findById(reserva.getId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELADO, guardada.getStatus());
        Payment pago = paymentRepository.findByReservationId(reserva.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED_PARTIAL, pago.getStatus(),
                "faltan más de 24h: se devuelve el 90% y la plataforma retiene el 10%");
        assertEquals(0, pago.getRefund().compareTo(
                pago.getAmount().multiply(new BigDecimal("0.90")).setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void participanteCancelaConMenosDe24hDevuelve50() throws Exception {
        Long instanceId = fechaEnMenosDe24h();
        mockMvc.perform(post("/checkout/" + instanceId)
                        .with(user("demo_user")).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection());
        Reservation reserva = reservationRepository
                .findFirstByInstanceIdAndUserIdAndStatus(
                        instanceId, usuario("demo_user").getId(), ReservationStatus.PENDIENTE)
                .orElseThrow();
        String sessionId = paymentRepository.findByReservationId(reserva.getId())
                .orElseThrow().getStripeSessionId();
        mockMvc.perform(get("/pago/exito").param("session_id", sessionId)
                        .with(user("demo_user")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/reservations/" + reserva.getId() + "/cancel")
                        .with(user("demo_user")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("ok", containsString("50%")));

        Payment pago = paymentRepository.findByReservationId(reserva.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED_PARTIAL, pago.getStatus());
        assertEquals(0, pago.getRefund().compareTo(
                pago.getAmount().multiply(new BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void creadorCancelaFechaYLosInscritosVenLaNotificacion() throws Exception {
        Long instanceId = fechaFutura();
        reservationService.reserve(instanceId, 1, usuario("demo_user"));

        mockMvc.perform(post("/instances/" + instanceId + "/cancel")
                        .with(user("demo_creator")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(InstanceStatus.CANCELLED,
                instanceRepository.findById(instanceId).orElseThrow().getStatus());

        mockMvc.perform(get("/notifications").with(user("demo_user")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cancelada")));
    }

    @Test
    void reservarYConfirmarGeneranNotificaciones() throws Exception {
        Long instanceId = fechaFutura();

        // Flujo Stripe nuevo: el checkout crea la PENDIENTE + notifica al creador.
        mockMvc.perform(post("/checkout/" + instanceId)
                        .with(user("demo_user")).with(csrf())
                        .param("attendees", "1"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/notifications").with(user("demo_creator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("reserv")));

        Reservation pendiente = reservationRepository
                .findFirstByInstanceIdAndUserIdAndStatus(
                        instanceId, usuario("demo_user").getId(), ReservationStatus.PENDIENTE)
                .orElseThrow();

        mockMvc.perform(post("/reservations/" + pendiente.getId() + "/confirm")
                        .with(user("demo_creator")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/notifications").with(user("demo_user")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("confirmada")));
    }

    @Test
    void sinSesionLasCancelacionesRedirigenALogin() throws Exception {
        mockMvc.perform(post("/reservations/1/cancel").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/instances/1/cancel").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/notifications"))
                .andExpect(status().is3xxRedirection());
    }
}