
package com.openlab.mx.share.identity.controller;

import com.openlab.mx.share.identity.client.DiditWebhookSigner;
import com.openlab.mx.share.identity.entity.DiditVerification;
import com.openlab.mx.share.identity.entity.VerificationStatus;
import com.openlab.mx.share.identity.repository.DiditVerificationRepository;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook de Didit end-to-end: la firma se calcula con el mismo algoritmo
 * que usará Didit en prod (secret de test: whsec_test).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiditWebhookControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DiditVerificationRepository verificationRepository;

    private final DiditWebhookSigner signer = new DiditWebhookSigner("whsec_test");

    private String firmar(String body) {
        return signer.sign(signer.canonicalize(body));
    }

    private String timestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    /** Usuario único por test: la H2 es compartida entre contextos (UNIQUE user_id). */
    private User usuarioNuevo() {
        String username = "kycwh-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@x.com")
                .passwordHash("{noop}x")
                .role(com.openlab.mx.share.user.entity.Role.USER)
                .birthDate(java.time.LocalDate.of(1990, 1, 1))
                .build());
    }

    private DiditVerification enCursoPara(User user) {
        return verificationRepository.save(DiditVerification.started(user.getId(), "sess-" + user.getId()));
    }

    private String approvedBody(User user, String eventId) {
        return "{\"event_id\":\"" + eventId + "\",\"webhook_type\":\"status.updated\","
                + "\"session_id\":\"sess-" + user.getId() + "\","
                + "\"vendor_data\":\"" + user.getId() + "\","
                + "\"status\":\"Approved\"}";
    }

    @Test
    void firma_invalida_responde_401() throws Exception {
        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", "deadbeef")
                        .header("X-Timestamp", timestamp())
                        .content("{\"event_id\":\"e1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void timestamp_viejo_responde_401() throws Exception {
        String body = "{\"event_id\":\"e1\"}";

        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", firmar(body))
                        .header("X-Timestamp", String.valueOf(Instant.now().getEpochSecond() - 4000))
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sin_event_id_responde_400() throws Exception {
        String body = "{\"webhook_type\":\"status.updated\",\"status\":\"Approved\"}";

        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", firmar(body))
                        .header("X-Timestamp", timestamp())
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void json_que_no_es_objeto_responde_400() throws Exception {
        String body = "[1,2,3]";

        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", firmar(body))
                        .header("X-Timestamp", timestamp())
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approved_marca_verified_en_bd() throws Exception {
        User user = usuarioNuevo();
        DiditVerification row = enCursoPara(user);
        String body = approvedBody(user, "e-approved-" + user.getId());

        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", firmar(body))
                        .header("X-Timestamp", timestamp())
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("processed")));

        DiditVerification despues = verificationRepository.findById(row.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(VerificationStatus.VERIFIED, despues.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(despues.getVerifiedAt());
    }

    @Test
    void evento_duplicado_no_duplica_efecto() throws Exception {
        User user = usuarioNuevo();
        DiditVerification row = enCursoPara(user);
        String body = approvedBody(user, "e-dup-" + user.getId());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/webhooks/didit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Signature-V2", firmar(body))
                            .header("X-Timestamp", timestamp())
                            .content(body))
                    .andExpect(status().isOk());
        }

        // reentrega: idempotencia por event_id
        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", firmar(body))
                        .header("X-Timestamp", timestamp())
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("duplicate")));

        org.junit.jupiter.api.Assertions.assertEquals(VerificationStatus.VERIFIED,
                verificationRepository.findById(row.getId()).orElseThrow().getStatus());
    }

    @Test
    void evento_sin_fila_se_ignora() throws Exception {
        String body = "{\"event_id\":\"e-sin-fila-" + java.util.UUID.randomUUID()
                + "\",\"webhook_type\":\"status.updated\","
                + "\"session_id\":\"sesion-inexistente\",\"vendor_data\":\"999999\","
                + "\"status\":\"Approved\"}";

        mockMvc.perform(post("/api/webhooks/didit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature-V2", firmar(body))
                        .header("X-Timestamp", timestamp())
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ignored")));
    }
}