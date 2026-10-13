package com.openlab.mx.share.user;

import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repro del bug reportado: en /settings se aprieta "Guardar", aparece
 * "Perfil actualizado" pero nada cambia (nombre, país, moneda, foto).
 * El test relee el usuario directo del repositorio después del POST.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsUpdateTests {

    private static final byte[] PNG_1PX = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private String crearUsuario() {
        String base = "u-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
        return base;
    }

    @Test
    void guardarConfigPersisteNombrePaisMonedaYFoto() throws Exception {
        String original = crearUsuario();
        String nuevoNombre = original + "-x";

        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", PNG_1PX);

        mockMvc.perform(multipart("/settings/update").file(avatar)
                        .param("username", nuevoNombre)
                        .param("country", "Argentina")
                        .param("currencyCode", "ARS")
                        .with(SecurityMockMvcRequestPostProcessors.user(original))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        // Relectura fresca directo de la BD: tiene que estar todo.
        User guardado = userRepository.findByUsername(nuevoNombre).orElseThrow(
                () -> new AssertionError("El nombre nuevo no se persistió"));
        org.junit.jupiter.api.Assertions.assertEquals("Argentina", guardado.getCountry(),
                "el país no se guardó");
        org.junit.jupiter.api.Assertions.assertEquals("ARS", guardado.getCurrencyCode(),
                "la moneda no se guardó");
        org.junit.jupiter.api.Assertions.assertNotNull(guardado.getAvatarBase64(),
                "la foto de perfil no se guardó");
        org.junit.jupiter.api.Assertions.assertTrue(
                !guardado.getAvatarBase64().isBlank(), "la foto de perfil quedó vacía");
    }

    @Test
    void settingsMuestraLosValoresRecienGuardados() throws Exception {
        String original = crearUsuario();
        String nuevoNombre = original + "-y";

        mockMvc.perform(multipart("/settings/update")
                        .param("username", nuevoNombre)
                        .param("country", "Chile")
                        .param("currencyCode", "CLP")
                        .with(SecurityMockMvcRequestPostProcessors.user(original))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/settings")
                        .with(SecurityMockMvcRequestPostProcessors.user(nuevoNombre)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nuevoNombre)))
                .andExpect(content().string(containsString("selected=\"selected\">Chile")));
    }
}
