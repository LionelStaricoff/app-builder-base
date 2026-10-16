
package com.openlab.mx.share.user;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginSeMuestra() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void registroDeMenorVuelveAlFormConMensajeDeError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "menor-" + UUID.randomUUID())
                        .param("email", "menor@x.com")
                        .param("password", "Clave1234!")
                        .param("birthDate", LocalDate.now().minusYears(10).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("18")));
    }

    @Test
    void registroExitosoRedirigeALogin() throws Exception {
        String username = "new-" + UUID.randomUUID();
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", username)
                        .param("email", username + "@x.com")
                        .param("password", "Clave1234!")
                        .param("birthDate", LocalDate.of(1995, 4, 12).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void sinSesionEnFeedRedirigeALogin() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginDemoRedirigeAFeed() throws Exception {
        mockMvc.perform(SecurityMockMvcRequestBuilders.formLogin()
                        .user("demo_user").password("Clave1234!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
    }
}