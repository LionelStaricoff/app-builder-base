package com.openlab.mx.share.interest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gate del feed + los pasos del onboarding de intereses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    /** Usuario nuevo: interestsOnboarded es false por default. */
    private User usuarioNuevo() {
        String base = "u-onb-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
    }

    @Test
    void feedGateaAlUsuarioNuevo() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/feed").with(user(nuevo.getUsername())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding/intereses"));
    }

    @Test
    void feedNoGateaAlUsuarioQueYaVioElOnboarding() throws Exception {
        mockMvc.perform(get("/feed").with(user("demo_user")))
                .andExpect(status().isOk());
    }

    @Test
    void feedApiNoGatea() throws Exception {
        User nuevo = usuarioNuevo();

        mockMvc.perform(get("/feed/api").param("page", "0").with(user(nuevo.getUsername())))
                .andExpect(status().isOk());
    }
}
