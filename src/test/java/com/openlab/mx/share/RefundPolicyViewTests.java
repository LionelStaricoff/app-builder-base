package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.reservation.service.ReservationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La política de reembolso se ve ANTES de reservar (detalle y checkout) y el
 * botón de cancelar dice cuánto se devuelve según el tiempo restante.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({com.openlab.mx.share.shared.TestGatewayConfig.class,
        com.openlab.mx.share.shared.TestIdentityGateConfig.class})
class RefundPolicyViewTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private UserRepository userRepository;

    private User usuario(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Long instanciaEnDias() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                "Policy Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), usuario("demo_creator"));
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0).getId();
    }

    @Test
    @WithMockUser("demo_user")
    void elDetalleMuestraElCartelDeReembolsoAntesDeReservar() throws Exception {
        Long instanceId = instanciaEnDias();
        Long experienceId = instanceRepository.findById(instanceId).orElseThrow()
                .getExperience().getId();

        mockMvc.perform(get("/experiences/" + experienceId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Política de cancelación")))
                .andExpect(content().string(containsString("90%")))
                .andExpect(content().string(containsString("50%")));
    }

    @Test
    @WithMockUser("demo_user")
    void elCheckoutMuestraElCartelYElPorcentajeDeEstaFecha() throws Exception {
        Long instanceId = instanciaEnDias();

        mockMvc.perform(get("/checkout/" + instanceId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Política de cancelación")))
                .andExpect(content().string(containsString("90%")));
    }

    @Test
    @WithMockUser("demo_user")
    void elBotonDeCancelarDiceElPorcentajeADevolver() throws Exception {
        Long instanceId = instanciaEnDias();
        reservationService.reserve(instanceId, 1, usuario("demo_user"));

        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cancelar reserva · devuelve 90%")));
    }
}
