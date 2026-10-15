package com.openlab.mx.share.identity.controller;

import com.openlab.mx.share.identity.entity.DiditVerification;
import com.openlab.mx.share.identity.entity.VerificationStatus;
import com.openlab.mx.share.identity.repository.DiditVerificationRepository;
import com.openlab.mx.share.shared.TestDiditConfig;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDiditConfig.class)
class VerificationPageControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DiditVerificationRepository verificationRepository;

    private User usuarioNuevo() {
        String username = "kycpage-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@x.com")
                .passwordHash("{noop}x")
                .role(com.openlab.mx.share.user.entity.Role.USER)
                .birthDate(LocalDate.of(1990, 1, 1))
                .build());
    }

    @Test
    void sin_sesion_redirige_a_login() throws Exception {
        mockMvc.perform(get("/verificacion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void verificacion_muestra_boton_cuando_no_verificado() throws Exception {
        User u = usuarioNuevo();

        mockMvc.perform(get("/verificacion").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verificación de identidad")))
                .andExpect(content().string(containsString("start-verification")));
    }

    @Test
    void verificacion_muestra_estado_verified() throws Exception {
        User u = usuarioNuevo();
        DiditVerification v = DiditVerification.started(u.getId(), "s1");
        v.applyDecision(VerificationStatus.VERIFIED, "Approved");
        verificationRepository.save(v);

        mockMvc.perform(get("/verificacion").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("identidad está verificada")));
    }

    @Test
    void callback_renderiza_polling() throws Exception {
        User u = usuarioNuevo();

        mockMvc.perform(get("/verificacion/callback").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("callback-status")));
    }

    @Test
    void returnUrl_externo_se_descarta() throws Exception {
        User u = usuarioNuevo();

        mockMvc.perform(get("/verificacion?returnUrl=//evil.com/robar")
                        .with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"/feed\"")));
    }
}
