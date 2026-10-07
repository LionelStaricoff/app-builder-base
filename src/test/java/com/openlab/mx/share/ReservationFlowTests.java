package com.openlab.mx.share;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    @Autowired
    private UserRepository userRepository;

    private Long fechaFutura() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                "Checkout Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        return instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0).getId();
    }

    @Test
    @WithMockUser("demo_user")
    void flujoReservaCheckoutYConfirmation() throws Exception {
        Long instanceId = fechaFutura();

        mockMvc.perform(post("/reservations/" + instanceId).with(csrf())
                        .param("attendees", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout/" + instanceId));

        mockMvc.perform(get("/checkout/" + instanceId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$")));

        mockMvc.perform(post("/checkout/" + instanceId).with(csrf())
                        .param("cardNumber", "123456789012")
                        .param("holder", "Demo User")
                        .param("expiry", "12/30")
                        .param("cvv", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/confirmation"));

        mockMvc.perform(get("/confirmation"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PAGASTE")));
    }

    @Test
    void sinSesionLosFluujosRedirigenALogin() throws Exception {
        mockMvc.perform(post("/reservations/1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/checkout/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/checkout/1").with(csrf())
                        .param("cardNumber", "123456789012")
                        .param("holder", "Demo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/confirmation"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
