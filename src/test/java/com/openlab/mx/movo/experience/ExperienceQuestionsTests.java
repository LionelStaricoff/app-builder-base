package com.openlab.mx.movo.experience;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceQuestionRepository;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preguntas publicas del detalle: las hace cualquier cuenta y las responde solo
 * la anfitriona. Antes esa duda se resolvia por chat privado despues de pagar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityGateConfig.class)
class ExperienceQuestionsTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceQuestionRepository questionRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void laPreguntaQuedaPublicadaEnElDetalle() throws Exception {
        User duenio = crearUsuario("duenio");
        Long expId = crearExperiencia(duenio);
        User curioso = crearUsuario("curioso");

        mockMvc.perform(post("/experiences/" + expId + "/questions")
                        .with(user(curioso.getUsername())).with(csrf())
                        .param("body", "Puedo ir aunque nunca haya tocado barro?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/experiences/" + expId + "#preguntas"));

        mockMvc.perform(get("/experiences/" + expId).with(user(curioso.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Puedo ir aunque nunca haya tocado barro")))
                .andExpect(content().string(containsString(curioso.getUsername())))
                .andExpect(content().string(containsString("Sin respuesta")));
    }

    @Test
    void laAnfitrionaRespondeYLaRespuestaQuedaPublica() throws Exception {
        User duenio = crearUsuario("duenio-responde");
        Long expId = crearExperiencia(duenio);
        User curioso = crearUsuario("curioso-responde");
        preguntar(expId, curioso, "Puedo ir aunque nunca haya tocado barro?");
        Long questionId = questionRepository
                .findByExperienceIdOrderByCreatedAtDescIdDesc(expId).get(0).getId();

        mockMvc.perform(post("/questions/" + questionId + "/answer")
                        .with(user(duenio.getUsername())).with(csrf())
                        .param("answer", "Claro: el taller esta pensado para quien empieza de cero."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/experiences/" + expId + "#preguntas"));

        mockMvc.perform(get("/experiences/" + expId).with(user(curioso.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Claro: el taller esta pensado para quien empieza de cero.")))
                .andExpect(content().string(containsString("Responde " + duenio.getUsername())))
                .andExpect(content().string(not(containsString("Sin respuesta"))));
    }

    @Test
    void soloLaAnfitrionaPuedeResponder() throws Exception {
        User duenio = crearUsuario("duenio-cerrado");
        Long expId = crearExperiencia(duenio);
        User curioso = crearUsuario("curioso-cerrado");
        User intruso = crearUsuario("intruso");
        preguntar(expId, curioso, "Se puede pagar en efectivo?");
        Long questionId = questionRepository
                .findByExperienceIdOrderByCreatedAtDescIdDesc(expId).get(0).getId();

        mockMvc.perform(post("/questions/" + questionId + "/answer")
                        .with(user(intruso.getUsername())).with(csrf())
                        .param("answer", "Yo respondo por ella"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("Solo quien organiza")));

        assertThat(questionRepository.findById(questionId).orElseThrow().getAnswer()).isNull();
    }

    @Test
    void laAnfitrionaNoSePreguntaASiMisma() throws Exception {
        User duenio = crearUsuario("duenio-propio");
        Long expId = crearExperiencia(duenio);

        mockMvc.perform(post("/experiences/" + expId + "/questions")
                        .with(user(duenio.getUsername())).with(csrf())
                        .param("body", "Hay lugar para sillas de ruedas?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("Las preguntas las hacen")));

        assertThat(questionRepository.countByExperienceId(expId)).isZero();
    }

    @Test
    void unaPreguntaDemasiadoCortaNoSeGuarda() throws Exception {
        User duenio = crearUsuario("duenio-corta");
        Long expId = crearExperiencia(duenio);
        User curioso = crearUsuario("curioso-corta");

        mockMvc.perform(post("/experiences/" + expId + "/questions")
                        .with(user(curioso.getUsername())).with(csrf())
                        .param("body", "ok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("entre 3 y 500")));

        assertThat(questionRepository.countByExperienceId(expId)).isZero();
    }

    private void preguntar(Long expId, User autor, String body) throws Exception {
        mockMvc.perform(post("/experiences/" + expId + "/questions")
                        .with(user(autor.getUsername())).with(csrf())
                        .param("body", body))
                .andExpect(status().is3xxRedirection());
    }

    private Long crearExperiencia(User duenio) {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                "Exp Preguntas " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), duenio).getId();
    }

    private User crearUsuario(String prefijo) {
        String base = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("Mexico")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
    }
}
