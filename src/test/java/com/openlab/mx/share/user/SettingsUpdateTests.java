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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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

    /**
     * Regresión del bug real: con foto pesada (sobre el límite) el controller viejo
     * guardaba el username ANTES de validar el avatar y descartaba
     * país/moneda en el early return. Todo el form es atómico: o se
     * guarda todo o no se guarda nada.
     */
    @Test
    void avatarMuyPesadoNoGuardaNadaNiNombreNiPais() throws Exception {
        String original = crearUsuario();
        String nuevoNombre = original + "-z";
        byte[] pesado = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile avatarGrande = new MockMultipartFile(
                "avatar", "foto.png", "image/png", pesado);

        mockMvc.perform(multipart("/settings/update").file(avatarGrande)
                        .param("username", nuevoNombre)
                        .param("country", "Argentina")
                        .param("currencyCode", "ARS")
                        .with(SecurityMockMvcRequestPostProcessors.user(original))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("error", "El avatar no puede superar 5 MB"));

        // Nada se persistió: el nombre nuevo no existe y el original sigue intacto.
        org.junit.jupiter.api.Assertions.assertTrue(
                userRepository.findByUsername(nuevoNombre).isEmpty(),
                "con avatar inválido el nombre no debería haberse guardado");
        User intacto = userRepository.findByUsername(original).orElseThrow(
                () -> new AssertionError("el usuario original debería seguir existiendo"));
        org.junit.jupiter.api.Assertions.assertEquals("México", intacto.getCountry(),
                "con avatar inválido el país no debería haberse guardado");
        org.junit.jupiter.api.Assertions.assertEquals("MXN", intacto.getCurrencyCode(),
                "con avatar inválido la moneda no debería haberse guardado");
        org.junit.jupiter.api.Assertions.assertNull(intacto.getAvatarBase64(),
                "con avatar inválido la foto no debería haberse guardado");
    }

    /**
     * El límite del avatar es 5 MB como las fotos de experiencias:
     * una foto de ~2 MB (típica de teléfono) tiene que guardarse.
     */
    @Test
    void avatarDe2MBSeGuardaConLimiteDe5MB() throws Exception {
        String original = crearUsuario();
        byte[] foto = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "foto.png", "image/png", foto);

        mockMvc.perform(multipart("/settings/update").file(avatar)
                        .param("username", original)
                        .param("country", "México")
                        .param("currencyCode", "MXN")
                        .with(SecurityMockMvcRequestPostProcessors.user(original))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("ok", "Perfil actualizado"));

        User guardado = userRepository.findByUsername(original).orElseThrow(
                () -> new AssertionError("el usuario debería seguir existiendo"));
        org.junit.jupiter.api.Assertions.assertNotNull(guardado.getAvatarBase64(),
                "la foto de ~2 MB debería haberse guardado con límite de 5 MB");
    }

    /**
     * Mensaje honesto: si no cambió nada, no se dice "Perfil actualizado".
     */
    @Test
    void sinCambiosAvisaSinCambios() throws Exception {
        String original = crearUsuario();

        mockMvc.perform(multipart("/settings/update")
                        .param("username", original)
                        .param("country", "México")
                        .param("currencyCode", "MXN")
                        .with(SecurityMockMvcRequestPostProcessors.user(original))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("ok", "Sin cambios para guardar"));
    }
}
