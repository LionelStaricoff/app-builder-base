package com.openlab.mx.share.payment;

import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.repository.ConnectAccountRepository;
import com.openlab.mx.share.shared.TestGatewayConfig;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión del bug real: un creador con país "Colombia" explotaba con
 * {@code CO is not currently supported by Stripe; code: country_unsupported}
 * (Stripe no soporta cuentas en Colombia, ver stripe.com/global).
 *
 * Ahora el país de cobro lo elige el creador SOLO entre la lista blanca de
 * Stripe (selector en creators/onboarding.html); un país fuera de la lista
 * responde 400 COUNTRY_UNSUPPORTED en vez de un 500 con stack de Stripe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGatewayConfig.class)
class CreatorPayoutCountryTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectAccountRepository connectAccountRepository;

    private String crearUsuario(String pais) {
        String base = "co-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country(pais)
                .currencyCode("MXN")
                .role(Role.CREATOR)
                .spPoints(0)
                .build());
        return base;
    }

    /** El bug original: Colombia → 400 claro, nunca 500 con stack de Stripe. */
    @Test
    void paisNoSoportadoCODevuelve400EnLugarDe500() throws Exception {
        String usuario = crearUsuario("Colombia");

        mockMvc.perform(post("/api/creators/payout-account/onboarding-link")
                        .param("country", "CO")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(SecurityMockMvcRequestPostProcessors.user(usuario).roles("CREATOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUNTRY_UNSUPPORTED"))
                .andExpect(jsonPath("$.message", containsString("Colombia")));
    }

    /** Sin país elegido el backend pide elegirlo (el select lo exige primero). */
    @Test
    void sinPaisDevuelve400PidiendoElegir() throws Exception {
        String usuario = crearUsuario("México");

        mockMvc.perform(post("/api/creators/payout-account/onboarding-link")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(SecurityMockMvcRequestPostProcessors.user(usuario).roles("CREATOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Elegí el país")));
    }

    /** País de la lista blanca → se crea la cuenta Connect y vuelve el link. */
    @Test
    void paisSoportadoMXCreaCuentaYDevuelveUrl() throws Exception {
        String usuario = crearUsuario("México");

        mockMvc.perform(post("/api/creators/payout-account/onboarding-link")
                        .param("country", "MX")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(SecurityMockMvcRequestPostProcessors.user(usuario).roles("CREATOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isNotEmpty());

        User user = userRepository.findByUsername(usuario).orElseThrow();
        var cuenta = connectAccountRepository.findByUser(user).orElseThrow();
        assertEquals("MX", cuenta.getCountry(), "el país elegido debe persistirse");
        assertEquals(ConnectAccountStatus.ONBOARDING_INCOMPLETE, cuenta.getStatus());
    }

    /** Regresión indirecta del bug: la página de cobros de un colombiano ya no
     * intenta crear la cuenta (y por lo tanto ya no revienta con 500). */
    @Test
    void paginaCobrosDeColombianoRespondeOKSinCuenta() throws Exception {
        String usuario = crearUsuario("Colombia");

        mockMvc.perform(get("/creators/payouts")
                        .with(SecurityMockMvcRequestPostProcessors.user(usuario)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No tenés cuenta Stripe Connect configurada")));
    }

    /** El selector de la página de onboarding solo lista países soportados. */
    @Test
    void onboardingMuestraSelectorSoloConPaisesSoportados() throws Exception {
        String usuario = crearUsuario("Colombia");

        mockMvc.perform(get("/creators/onboarding")
                        .with(SecurityMockMvcRequestPostProcessors.user(usuario)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"payout-country\"")))
                .andExpect(content().string(containsString("País de tu cuenta bancaria")))
                .andExpect(content().string(containsString("México")))
                // Colombia no puede aparecer como opción de cobro.
                .andExpect(content().string(not(containsString("Colombia"))))
                // País no soportado del perfil → nada preseleccionado.
                .andExpect(content().string(not(containsString("selected=\"selected\""))));
    }

    /** Un mexicano sí ve preseleccionado su país (soportado por Stripe). */
    @Test
    void onboardingPrecargaPaisSoportadoDelPerfil() throws Exception {
        String usuario = crearUsuario("México");

        mockMvc.perform(get("/creators/onboarding")
                        .with(SecurityMockMvcRequestPostProcessors.user(usuario)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("selected=\"selected\">México")));
    }
}
