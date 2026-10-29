package com.openlab.mx.movo.proposal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gate 2 del feed (spec §3): con proposalOnboarded=false el /feed manda a la
 * pantalla 3; /feed/cards NO se gatea (el scroll infinito necesita el HTML).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedProposalGateTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User usuarioNuevo() {
        String base = "u-gate-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                // El gate de intereses (gate 1) pasa: este test mide el gate 2.
                .interestsOnboarded(true)
                .build());
    }

    @Test
    void conFlagFalseRedirigeALaPropuesta() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/feed").with(user(nuevo.getUsername())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding/propuesta"));
    }

    @Test
    void conFlagTrueMuestraElFeed() throws Exception {
        mockMvc.perform(get("/feed").with(user("demo_user")))
                .andExpect(status().isOk());
    }

    @Test
    void feedCardsNoSeGatea() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/feed/cards").with(user(nuevo.getUsername())))
                .andExpect(status().isOk());
    }
}
