package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import org.springframework.context.annotation.Import;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review Focus 5 (spec §8): sin STRIPE_SECRET_KEY el POST no se cuelga con
 * NPE — mensaje claro y nada persistido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.openlab.mx.share.shared.TestIdentityGateConfig.class)
class UnconfiguredCheckoutTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private com.openlab.mx.share.reservation.repository.ReservationRepository reservationRepository;
    @Autowired
    private UserRepository userRepository;

    private Long fechaFutura() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        var experience = experienceService.create(new ExperienceFormRequest(
                "NoKeys Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId())
                .get(0).getId();
    }

    @Test
    @WithMockUser("demo_user")
    void postSinKeysFallaConMensajeClaroYNoCreaNada() throws Exception {
        Long instanceId = fechaFutura();

        mockMvc.perform(post("/checkout/" + instanceId).with(csrf()).param("attendees", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error",
                        containsString("El pago no está disponible")));

        // La transacción completa se revirtió: ni reserva PENDIENTE ni payment.
        org.junit.jupiter.api.Assertions.assertTrue(
                reservationRepository.findFirstByInstanceIdAndUserIdAndStatus(instanceId,
                        userRepository.findByUsername("demo_user").orElseThrow().getId(),
                        ReservationStatus.PENDIENTE).isEmpty(),
                "rollback completo: el POST sin keys no deja reserva");
        org.junit.jupiter.api.Assertions.assertTrue(mappedPagos(instanceId));
    }

    private boolean mappedPagos(Long instanceId) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getReservation() != null
                        && p.getReservation().getInstance() != null
                        && instanceId.equals(p.getReservation().getInstance().getId()))
                .toList().isEmpty();
    }
}
