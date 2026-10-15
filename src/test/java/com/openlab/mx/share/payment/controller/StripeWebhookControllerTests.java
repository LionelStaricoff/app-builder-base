package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.payment.entity.StripeEvent;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.payment.repository.StripeEventRepository;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec §7: webhook con firma obligatoria, idempotencia por event_id,
 * eventos implemented: completed→HELD, expired→FAILED+reserva CANCELADO,
 * payment_failed→FAILED; desconocidos 200 sin efecto.
 */
// OJO: sin @SpringBootTest(properties=...) — ese extra cambiaba la clave de caché
// del contexto y creaba un SEGUNDO contexto (con su propio FakePaymentGateway cuya
// secuencia arranca de nuevo en cs_fake_1). Todos los contextos comparten la misma
// H2 de test, así que los session ids se pisaban entre contextos y rompía
// PaymentSuccessFlowTests (findFirstByStripeSessionId devolvía la fila de otro test).
// El secret vive en src/test/resources/application.properties (compartido).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGatewayConfig.class)
@WithMockUser("demo_user")
class StripeWebhookControllerTests {

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
    private StripeEventRepository stripeEventRepository;
    @Autowired
    private UserRepository userRepository;

    private static final String SECRET = "whsec_test";

    private Long fechaFutura() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        var experience = experienceService.create(new ExperienceFormRequest(
                "Webhook Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId())
                .get(0).getId();
    }

    /** Crea reserva+payment (PENDING) vía el flujo web y devuelve el session id. */
    private String armaSessionId() throws Exception {
        Long instanceId = fechaFutura();
        mockMvc.perform(post("/checkout/" + instanceId).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection());
        var reservation = reservationRepository.findFirstByInstanceIdAndUserIdAndStatus(
                instanceId, userRepository.findByUsername("demo_user").orElseThrow().getId(),
                ReservationStatus.PENDIENTE).orElseThrow();
        return paymentRepository.findByReservationId(reservation.getId())
                .orElseThrow().getStripeSessionId();
    }

    private String firma(String payload) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(
                mac.doFinal((ts + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + ts + ",v1=" + v1;
    }

    @Test
    void completedPoneHeldYGuardaEvento() throws Exception {
        String sessionId = armaSessionId();
        String payload = "{"
                + "\"id\":\"evt_completed_1\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(payload))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());

        var pago = paymentRepository.findFirstByStripeSessionId(sessionId).orElseThrow();
        assertEquals(PaymentStatus.HELD, pago.getStatus());
        assertTrue(pago.getStripePaymentIntentId().startsWith("pi_fake_"));
        assertTrue(stripeEventRepository.existsById("evt_completed_1"));
    }

    @Test
    void eventoDuplicadoNoTieneDobleEfecto() throws Exception {
        String sessionId = armaSessionId();
        String payload = "{"
                + "\"id\":\"evt_dup_1\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(payload))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());
        var heldAt = paymentRepository.findFirstByStripeSessionId(sessionId)
                .orElseThrow().getHeldAt();
        org.junit.jupiter.api.Assertions.assertNotNull(heldAt);

        // retry del mismo event_id → 200 y sin doble efecto
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(payload))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());
        var heldAtDespues = paymentRepository.findFirstByStripeSessionId(sessionId)
                .orElseThrow().getHeldAt();
        org.junit.jupiter.api.Assertions.assertEquals(heldAt, heldAtDespues,
                "idempotencia: heldAt intacto en el retry");
        assertEquals(PaymentStatus.HELD,
                paymentRepository.findFirstByStripeSessionId(sessionId).orElseThrow().getStatus());
    }

    @Test
    void firmaInvalidaDa400YNoPersiste() throws Exception {
        String sessionId = armaSessionId();
        String payload = "{"
                + "\"id\":\"evt_bad_1\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());
        assertTrue(!stripeEventRepository.existsById("evt_bad_1"));
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findFirstByStripeSessionId(sessionId).orElseThrow().getStatus());
    }

    @Test
    void expiredFallaElPagoYLiberaCupo() throws Exception {
        String sessionId = armaSessionId();
        String payload = "{"
                + "\"id\":\"evt_expired_1\",\"type\":\"checkout.session.expired\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(payload))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());

        var pago = paymentRepository.findFirstByStripeSessionId(sessionId).orElseThrow();
        assertEquals(PaymentStatus.FAILED, pago.getStatus());
        assertEquals(ReservationStatus.CANCELADO, pago.getReservation().getStatus());
    }

    @Test
    void paymentFailedDePagoHeldVaAFailed() throws Exception {
        String sessionId = armaSessionId();
        String payload = "{"
                + "\"id\":\"evt_ok_1\",\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(payload))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());
        String piId = paymentRepository.findFirstByStripeSessionId(sessionId)
                .orElseThrow().getStripePaymentIntentId();

        String fallo = "{"
                + "\"id\":\"evt_failed_1\",\"type\":\"payment_intent.payment_failed\","
                + "\"data\":{\"object\":{\"id\":\"" + piId + "\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(fallo))
                        .contentType("application/json")
                        .content(fallo))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.FAILED,
                paymentRepository.findFirstByStripeSessionId(sessionId).orElseThrow().getStatus());
    }

    @Test
    void eventoDesconocidoResponde200SinEfecto() throws Exception {
        String sessionId = armaSessionId();
        String payload = "{"
                + "\"id\":\"evt_unknown_1\",\"type\":\"customer.created\","
                + "\"data\":{\"object\":{\"id\":\"cus_1\"}}}";
        mockMvc.perform(post("/api/stripe/webhooks")
                        .header("Stripe-Signature", firma(payload))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findFirstByStripeSessionId(sessionId).orElseThrow().getStatus());
    }

    @Test
    void stripeEventEntidadPersisteConTipo() {
        StripeEvent e = StripeEvent.builder()
                .eventId("evt_x").type("prueba").build();
        assertEquals("evt_x", e.getEventId());
        assertEquals("prueba", e.getType());
    }
}
