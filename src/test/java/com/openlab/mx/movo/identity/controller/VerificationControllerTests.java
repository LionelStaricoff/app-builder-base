
package com.openlab.mx.movo.identity.controller;

import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.entity.VerificationStatus;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.shared.TestDiditConfig;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDiditConfig.class)
class VerificationControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DiditVerificationRepository verificationRepository;

    /** Usuario único por test: la H2 es compartida entre contextos. */
    private User usuarioNuevo() {
        String username = "kyc-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@x.com")
                .passwordHash("{noop}x")
                .role(com.openlab.mx.movo.user.entity.Role.USER)
                .birthDate(LocalDate.of(1990, 1, 1))
                .build());
    }

    @Test
    void sin_sesion_redirige_a_login() throws Exception {
        mockMvc.perform(get("/api/didit/status"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void post_sessions_devuelve_url_de_didit_y_crea_la_fila() throws Exception {
        User u = usuarioNuevo();

        mockMvc.perform(post("/api/didit/sessions")
                        .with(user(u.getUsername()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("fake-" + u.getId()))
                .andExpect(jsonPath("$.url").value("https://didit.me/fake/" + u.getId()));

        DiditVerification fila = verificationRepository.findByUserId(u.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(VerificationStatus.IN_PROGRESS, fila.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("fake-" + u.getId(), fila.getDiditSessionId());
    }

    @Test
    void post_sessions_sin_token_csrf_se_rechaza() throws Exception {
        User u = usuarioNuevo();

        // La endpoint ya no está en la lista de ignorados de CSRF: sin token
        // el pedido se rechaza y no se crea ninguna fila de verificación.
        mockMvc.perform(post("/api/didit/sessions")
                        .with(user(u.getUsername())))
                .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertTrue(
                verificationRepository.findByUserId(u.getId()).isEmpty());
    }

    @Test
    void la_pagina_de_verificacion_expone_el_token_csrf() throws Exception {
        User u = usuarioNuevo();

        mockMvc.perform(get("/verificacion").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void get_status_sin_fila_devuelve_not_started() throws Exception {
        User u = usuarioNuevo();

        mockMvc.perform(get("/api/didit/status").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.verifiedAt").doesNotExist());
    }

    @Test
    void post_sobre_verified_responde_409() throws Exception {
        User u = usuarioNuevo();
        DiditVerification verificada = DiditVerification.started(u.getId(), "s1");
        verificada.applyDecision(VerificationStatus.VERIFIED, "Approved");
        verificationRepository.save(verificada);

        mockMvc.perform(post("/api/didit/sessions")
                        .with(user(u.getUsername()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_VERIFIED"));

        // y la fila no cambió: sigue con la sesión original
        Optional<DiditVerification> despues = verificationRepository.findByUserId(u.getId());
        org.junit.jupiter.api.Assertions.assertEquals("s1", despues.orElseThrow().getDiditSessionId());
    }
}