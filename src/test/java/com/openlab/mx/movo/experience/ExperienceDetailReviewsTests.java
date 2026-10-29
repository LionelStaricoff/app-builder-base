package com.openlab.mx.movo.experience;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.Comment;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.entity.Rating;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.CommentRepository;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.experience.repository.RatingRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.shared.TestIdentityGateConfig;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reseñas con texto en el detalle de la experiencia (spec del prototipo:
 * "Reseñas" con autor, estrellas y comentario).
 *
 * Hoy Rating + Comment se guardan al terminar una fecha (RatingService.rate)
 * pero el detalle nunca los listaba: solo mostraba el promedio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityGateConfig.class)
class ExperienceDetailReviewsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void muestraTextoYAutorDeLaResena() throws Exception {
        Long expId = crearExperiencia();
        ExperienceInstance fecha = instanciaPasada(expId);
        User autor = crearUsuario("u-resena");
        User observador = crearUsuario("observador");
        ratingRepository.save(Rating.builder().instance(fecha).user(autor).stars(5).build());
        commentRepository.save(Comment.builder()
                .instance(fecha).user(autor)
                .text("Una locura: la anfitriona explica con paciencia")
                .build());

        mockMvc.perform(get("/experiences/" + expId).with(user(observador.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reseñas")))
                .andExpect(content().string(containsString("(1)")))
                .andExpect(content().string(containsString("Una locura: la anfitriona explica con paciencia")))
                .andExpect(content().string(containsString(autor.getUsername())));
    }

    @Test
    void sinResenasMuestraEmptyState() throws Exception {
        Long expId = crearExperiencia();
        User observador = crearUsuario("observador");

        mockMvc.perform(get("/experiences/" + expId).with(user(observador.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no hay reseñas")));
    }

    @Test
    void detalleMuestraQueIncluyeYQueLlevar() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Long expId = experienceService.create(new ExperienceFormRequest(
                "Exp Contenido " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image),
                "Entrada al museo\nGuía bilingüe",
                "Calzado cómodo\nProtector solar"),
                crearUsuario("creador")).getId();
        User observador = crearUsuario("observador-contenido");

        mockMvc.perform(get("/experiences/" + expId).with(user(observador.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Qué incluye")))
                .andExpect(content().string(containsString("Guía bilingüe")))
                .andExpect(content().string(containsString("Qué llevar")))
                .andExpect(content().string(containsString("Protector solar")));
    }

    private Long crearExperiencia() {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                "Exp Reseñas " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), crearUsuario("creador")).getId();
    }

    /** Fecha terminada: es la que sostiene la reseña (rate() exige COMPLETED). */
    private ExperienceInstance instanciaPasada(Long expId) {
        var experiencia = experienceService.get(expId);
        LocalDateTime ayer = LocalDateTime.now().minusDays(3);
        ExperienceInstance fecha = ExperienceInstance.builder()
                .experience(experiencia)
                .date(ayer.toLocalDate())
                .startTime(ayer.toLocalTime())
                .endTime(ayer.toLocalTime().plusHours(2))
                .status(InstanceStatus.COMPLETED)
                .build();
        // El promedio del encabezado lo mantiene RatingService al calificar;
        // acá se fija a mano porque el test siembra sin pasar por rate().
        experiencia.setRatingAverage(5.0);
        experienceRepository.save(experiencia);
        return instanceRepository.save(fecha);
    }

    private User crearUsuario(String prefijo) {
        String base = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
    }
}
