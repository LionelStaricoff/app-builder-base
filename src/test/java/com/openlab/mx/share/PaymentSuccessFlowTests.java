
package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.payment.service.StripeCheckoutService;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.repository.NotificationRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review Focus 1 y 4 (spec §5-6): no duplica reserva/pago/sesión; el retorno
 * revalida contra la puerta real; usuario ajeno → 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestGatewayConfig.class, com.openlab.mx.share.shared.TestIdentityGateConfig.class})
class PaymentSuccessFlowTests {

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
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private StripeCheckoutService stripeCheckoutService;

    private Long fechaFutura() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        var experience = experienceService.create(new ExperienceFormRequest(
                "Stripe Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null),
                userRepository.findByUsername("demo_creator").orElseThrow());
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId())
                .get(0).getId();
    }

    private Payment pago(Long instanceId, String username) {
        var reservation = reservationRepository.findFirstByInstanceIdAndUserId(
                instanceId, userRepository.findByUsername(username).orElseThrow().getId()).orElseThrow();
        return paymentRepository.findByReservationId(reservation.getId()).orElseThrow();
    }

    @Test
    @WithMockUser("demo_user")
    void postDeReservaRedirigeAStripeYElRetornoPoneHeld() throws Exception {
        Long instanceId = fechaFutura();

        // (a) POST → 302 a la página hosted de Stripe (fake)
        String[] redirect = new String[1];
        mockMvc.perform(post("/reservations/" + instanceId).with(csrf()).param("attendees", "2"))
                .andExpect(status().is3xxRedirection())
                .andDo(r -> redirect[0] = r.getResponse().getRedirectedUrl());
        org.junit.jupiter.api.Assertions.assertNotNull(redirect[0]);
        org.junit.jupiter.api.Assertions.assertTrue(redirect[0].contains("/__fake_stripe_/checkout/"));

        assertEquals(PaymentStatus.PENDING, pago(instanceId, "demo_user").getStatus());

        // (c) retorno: /pago/exito revalida en la puerta real → HELD → PRG /confirmation
        String sessionId = pago(instanceId, "demo_user").getStripeSessionId();
        mockMvc.perform(get("/pago/exito").param("session_id", sessionId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/confirmation"));
        Payment held = pago(instanceId, "demo_user");
        assertEquals(PaymentStatus.HELD, held.getStatus());
        // Prefijo, no "pi_fake_1": con el contexto compartido la secuencia del
        // FakePaymentGateway sigue avanzando (CancellationFlow/E2e/webhook ya
        // crearon sesiones antes que este test en el suite completo).
        org.junit.jupiter.api.Assertions.assertTrue(
                held.getStripePaymentIntentId().startsWith("pi_fake_"),
                "paymentIntent del fake, pero era: " + held.getStripePaymentIntentId());
        assertNotNull(held.getHeldAt());
    }

    @Test
    @WithMockUser("demo_user")
    void dobleSubmitNoDuplicaReservaPagoNiSesion() throws Exception {
        Long instanceId = fechaFutura();

        String[] primera = new String[1];
        mockMvc.perform(post("/reservations/" + instanceId).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection())
                .andDo(r -> primera[0] = r.getResponse().getRedirectedUrl());
        org.junit.jupiter.api.Assertions.assertNotNull(primera[0]);
        // el reintento redirige a la MISMA listar/Checkout Session (Review Focus 1)
        mockMvc.perform(post("/reservations/" + instanceId).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(primera[0]));

        assertEquals(primera[0], "/__fake_stripe_/checkout/"
                        + pago(instanceId, "demo_user").getStripeSessionId(),
                "una sola sesión; el pago guardó la misma (Review Focus 1)");
        long reservas = reservationRepository.findAll().stream()
                .filter(r -> r.getInstance().getId().equals(instanceId))
                .count();
        assertEquals(1, reservas, "una sola reserva");
    }

    @Test
    void usuarioAjenoRecibe403YElPagoQuedaPendiente() throws Exception {
        Long instanceId = fechaFutura();
        mockMvc.perform(post("/reservations/" + instanceId)
                        .with(user("demo_user")).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection());
        Payment pago = pago(instanceId, "demo_user");

        // (d) otro usuario no completa el pago ajeno: 403 del handler NotOwnerException
        mockMvc.perform(get("/pago/exito").param("session_id", pago.getStripeSessionId())
                        .with(user("demo_creator")))
                .andExpect(status().isForbidden());
        assertEquals(PaymentStatus.PENDING,
                pago(instanceId, "demo_user").getStatus());
    }

    @Test
    @WithMockUser("demo_user")
    void sessionIdDesconocidoRespondeConElHandlerDeDominios() throws Exception {
        // DomainException → PRG con flash (no 500)
        mockMvc.perform(get("/pago/exito").param("session_id", "cs_fantasma"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser("demo_user")
    void sessionIdDesconocidoXhrRecibe409Json() throws Exception {
        // DomainException + XHR → JSON 409 con mensaje (un fetch no entiende PRG)
        mockMvc.perform(get("/pago/exito").param("session_id", "cs_fantasma")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    // ---------- instant book: el pago confirma y notifica (spec §6) ----------

    /** Flujo completo: reserva → sesión Stripe → retorno de Stripe. Devuelve el sessionId. */
    private String pagar(Long instanceId, String username) throws Exception {
        mockMvc.perform(post("/reservations/" + instanceId).with(csrf()).param("attendees", "2"))
                .andExpect(status().is3xxRedirection());
        String sessionId = pago(instanceId, username).getStripeSessionId();
        mockMvc.perform(get("/pago/exito").param("session_id", sessionId))
                .andExpect(status().is3xxRedirection());
        return sessionId;
    }

    private long contarConfirmadas(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getType() == NotificationType.RESERVA_CONFIRMADA)
                .count();
    }

    @Test
    @WithMockUser("demo_user")
    void elPagoConfirmaLaReservaYAbreElChat() throws Exception {
        Long instanceId = fechaFutura();
        pagar(instanceId, "demo_user");

        Long userId = userRepository.findByUsername("demo_user").orElseThrow().getId();
        Reservation r = reservationRepository.findFirstByInstanceIdAndUserId(instanceId, userId)
                .orElseThrow();
        assertEquals(ReservationStatus.CONFIRMADA, r.getStatus(), "el pago confirma la reserva");
        assertEquals(PaymentStatus.HELD, pago(instanceId, "demo_user").getStatus(),
                "el escrow no se libera al confirmar");
        mockMvc.perform(get("/chats/" + instanceId)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser("demo_user")
    void elPagoNotificaAlHuespedYAlCreador() throws Exception {
        notificationRepository.deleteAll();
        Long instanceId = fechaFutura();
        pagar(instanceId, "demo_user");

        Long huespedId = userRepository.findByUsername("demo_user").orElseThrow().getId();
        Long creadorId = userRepository.findByUsername("demo_creator").orElseThrow().getId();

        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(huespedId).stream().anyMatch(
                        n -> n.getType() == NotificationType.RESERVA_CONFIRMADA
                                && ("/chats/" + instanceId).equals(n.getLink())),
                "aviso al huésped con link al chat");
        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(creadorId).stream().anyMatch(
                        n -> n.getType() == NotificationType.RESERVA_CONFIRMADA
                                && "/reservations".equals(n.getLink())),
                "aviso al creador");
        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(creadorId).stream().noneMatch(
                        n -> n.getType() == NotificationType.SOLICITUD_RESERVA),
                "el aviso previo al cobro ya no existe");
    }

    @Test
    @WithMockUser("demo_user")
    void completeIdempotenteNoDuplicaConfirmacionNiAvisos() throws Exception {
        notificationRepository.deleteAll();
        Long instanceId = fechaFutura();
        String sessionId = pagar(instanceId, "demo_user");

        Long huespedId = userRepository.findByUsername("demo_user").orElseThrow().getId();
        Long creadorId = userRepository.findByUsername("demo_creator").orElseThrow().getId();
        long avisos = contarConfirmadas(huespedId) + contarConfirmadas(creadorId);
        assertEquals(2L, avisos, "el pago genera exactamente un aviso por destinatario");

        stripeCheckoutService.complete(sessionId, huespedId); // 2ª llamada (retorno/webhook repetido)

        assertEquals(avisos, contarConfirmadas(huespedId) + contarConfirmadas(creadorId),
                "la segunda llamada no duplica RESERVA_CONFIRMADA");
    }
}
