package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revisión previa al pago: el checkout mostraba solo fecha, cupos y total. Acá
 * se verifica el desglose (precio unitario × personas), que no se inventen
 * cargos extra, el método de pago y la política con el % de esa fecha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({com.openlab.mx.share.shared.TestGatewayConfig.class,
        com.openlab.mx.share.shared.TestIdentityGateConfig.class})
class CheckoutReviewTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private UserRepository userRepository;

    private User usuario(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Long instanciaEnDias() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                "Checkout Review " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), usuario("demo_creator"));
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0).getId();
    }

    @Test
    @WithMockUser("demo_user")
    void muestraElDesgloseDelPrecioYElTotal() throws Exception {
        Long instanceId = instanciaEnDias();

        mockMvc.perform(get("/checkout/" + instanceId).param("attendees", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Desglose")))
                .andExpect(content().string(containsString("Total a pagar")))
                // 100 x 2 personas: el desglose y el total coinciden.
                .andExpect(content().string(containsString("100.00")))
                .andExpect(content().string(containsString("200.00")))
                .andExpect(content().string(containsString("personas")))
                .andExpect(content().string(containsString("Sin cargos extra")));
    }

    @Test
    @WithMockUser("demo_user")
    void explicaComoSePagaSinInventarDatosDeTarjeta() throws Exception {
        Long instanceId = instanciaEnDias();

        mockMvc.perform(get("/checkout/" + instanceId).param("attendees", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stripe")))
                .andExpect(content().string(containsString("no guarda tu")))
                // No hay tarjeta guardada: nada de "•••• 4821".
                .andExpect(content().string(not(containsString("4821"))));
    }

    @Test
    @WithMockUser("demo_user")
    void muestraLaPoliticaConElPorcentajeDeEsaFecha() throws Exception {
        Long instanceId = instanciaEnDias();

        mockMvc.perform(get("/checkout/" + instanceId).param("attendees", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pol")))
                .andExpect(content().string(containsString("90%")))
                .andExpect(content().string(containsString("retenido en garant")))
                .andExpect(content().string(containsString("Pagar MXN")));
    }
}
