
package com.openlab.mx.share.user;
import org.junit.jupiter.api.Disabled;

import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simula el flujo real del navegador: login de verdad, GET /settings,
 * extracción del token CSRF y del action del HTML renderizado, y POST
 * multipart idéntico a lo que hace el form del usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsBrowserFlowTests {

    private static final byte[] PNG_1PX = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void flujoRealDeNavegadorGuardaTodo() throws Exception {
        String base = "b-" + UUID.randomUUID().toString().substring(0, 8);
        String rawPassword = "Clave1234!";
        userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());

        // 1. Login real (pasa por SharedUserDetailsService).
        MvcResult login = mockMvc.perform(SecurityMockMvcRequestBuilders.formLogin()
                        .user(base).password(rawPassword))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "el login no creó sesión");

        // 2. GET de la página como la vería el navegador.
        String html = mockMvc.perform(get("/settings").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Matcher actionM = Pattern.compile("action=\"([^\"]*settings/update[^\"]*)\"").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(actionM.find(),
                "el form de settings no apunta a /settings/update:\n"
                        + html.substring(0, Math.min(html.length(), 1500)));
        String action = actionM.group(1);

        Matcher csrfM = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(csrfM.find(), "no se encontró el token CSRF en la página");
        String token = csrfM.group(1);

        // 3. POST multipart exactamente como el navegador (action + token propios).
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", PNG_1PX);
        String nuevoNombre = base + "-nuevo";

        mockMvc.perform(multipart(action).file(avatar)
                        .param("_csrf", token)
                        .param("username", nuevoNombre)
                        .param("country", "Argentina")
                        .param("currencyCode", "ARS")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // 4. Relectura fresca de la BD.
        User guardado = userRepository.findByUsername(nuevoNombre)
                .orElseThrow(() -> new AssertionError("nombre no persistido"));
        assertEquals("Argentina", guardado.getCountry(), "país no persistido");
        assertEquals("ARS", guardado.getCurrencyCode(), "moneda no persistida");
        assertNotNull(guardado.getAvatarBase64(), "foto no persistida");
    }
}