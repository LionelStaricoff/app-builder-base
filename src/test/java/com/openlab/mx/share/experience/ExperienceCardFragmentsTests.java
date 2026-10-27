package com.openlab.mx.share.experience;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Comment;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.Rating;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.CommentRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.RatingRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.experience.service.QuestionService;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.TestGatewayConfig;
import com.openlab.mx.share.shared.TestIdentityGateConfig;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestGatewayConfig.class, TestIdentityGateConfig.class})
class ExperienceCardFragmentsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    private Experience experiencia() {
        User creador = userRepository.findByUsername("demo_creator").orElseThrow();
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                "Card Frag " + UUID.randomUUID(), "Desc", 120, 1, 6, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(
                        new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null),
                        new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(15, 0), LocalTime.of(17, 0), null, null)),
                List.of(image), null, null),
                creador);
    }

    @Test
    @WithMockUser("demo_user")
    void datesDevuelveTodasLasFechasConSuInstanceId() throws Exception {
        Experience experience = experiencia();
        List<ExperienceInstance> instancias =
                instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId());

        mockMvc.perform(get("/experiences/{id}/dates", experience.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-date-option")))
                .andExpect(content().string(containsString("data-instance-id=\"" + instancias.get(0).getId() + "\"")))
                .andExpect(content().string(containsString("data-instance-id=\"" + instancias.get(1).getId() + "\"")))
                .andExpect(content().string(containsString("15:00")));
    }

    @Test
    @WithMockUser("demo_user")
    void availabilityDevuelveLosCuposDeEsaFecha() throws Exception {
        Experience experience = experiencia();
        ExperienceInstance primera =
                instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0);
        User usuario = userRepository.findByUsername("demo_user").orElseThrow();
        reservationRepository.save(Reservation.builder()
                .instance(primera).user(usuario).attendees(2)
                .status(ReservationStatus.CONFIRMADA).build());

        mockMvc.perform(get("/experiences/{id}/dates/{instanceId}",
                        experience.getId(), primera.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("4 libres")))
                .andExpect(content().string(containsString("2 reservaron")))
                .andExpect(content().string(containsString("6 cupos")));
    }

    @Test
    @WithMockUser("demo_user")
    void questionsMuestraElFormDePreguntarAlVisitante() throws Exception {
        Experience experience = experiencia();

        mockMvc.perform(get("/experiences/{id}/questions", experience.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Preguntale a la anfitriona")))
                .andExpect(content().string(containsString("data-question-form")))
                // th:data-count (no data-count="${...}", que Thymeleaf deja literal):
                // el JS lee dataset.count para el badge del <summary>.
                .andExpect(content().string(containsString("data-count=\"0\"")))
                .andExpect(content().string(not(containsString("Publicar respuesta"))));
    }

    @Test
    @WithMockUser("demo_creator")
    void questionsMuestraElFormDeResponderAlDueno() throws Exception {
        Experience experience = experiencia();
        User asker = userRepository.findByUsername("demo_user").orElseThrow();
        questionService.ask(experience.getId(), asker, "¿Hay estacionamiento cerca?");

        mockMvc.perform(get("/experiences/{id}/questions", experience.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Publicar respuesta")))
                .andExpect(content().string(containsString("¿Hay estacionamiento cerca?")))
                .andExpect(content().string(not(containsString("Preguntale a la anfitriona"))));
    }

    @Test
    @WithMockUser("demo_user")
    void askFragmentDevuelveLaPreguntaPublicada() throws Exception {
        Experience experience = experiencia();

        mockMvc.perform(post("/experiences/{id}/questions/fragment", experience.getId())
                        .with(csrf())
                        .param("body", "¿Se puede ir con niños?"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("¿Se puede ir con niños?")))
                .andExpect(content().string(containsString("Pregunta enviada")));
    }

    @Test
    @WithMockUser("demo_user")
    void askFragmentConTextoDemasiadoCortoDevuelveElError() throws Exception {
        Experience experience = experiencia();

        mockMvc.perform(post("/experiences/{id}/questions/fragment", experience.getId())
                        .with(csrf())
                        .param("body", "ab"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Escribí una pregunta de entre 3 y 500 caracteres")));
    }

    @Test
    @WithMockUser("demo_user")
    void reviewsDevuelveLasReseniasSembradas() throws Exception {
        Experience experience = experiencia();
        ExperienceInstance primera =
                instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0);
        User usuario = userRepository.findByUsername("demo_user").orElseThrow();
        ratingRepository.save(Rating.builder()
                .instance(primera).user(usuario).stars(5).build());
        commentRepository.save(Comment.builder()
                .instance(primera).user(usuario).text("Una experiencia increíble").build());

        mockMvc.perform(get("/experiences/{id}/reviews", experience.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("demo_user")))
                .andExpect(content().string(containsString("Una experiencia increíble")))
                .andExpect(content().string(containsString("data-count=\"1\"")))
                .andExpect(content().string(containsString("5/5")));
    }

    @Test
    void sinSesionLosFragmentosRedirigenALogin() throws Exception {
        mockMvc.perform(get("/experiences/1/dates"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
