package com.openlab.mx.share.experience;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.TestIdentityGateConfig;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Quienes van" del detalle: solo cuentan las reservas CONFIRMADAS de fechas
 * futuras. Las pendientes no pagaron y las canceladas liberaron su lugar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityGateConfig.class)
class ExperienceGoingTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void muestraALasPersonasConReservaConfirmada() throws Exception {
        User duenio = crearUsuario("duenio-van");
        Long expId = crearExperiencia(duenio);
        ExperienceInstance fecha = instanciaFutura(expId);
        User ana = crearUsuario("ana");
        reservar(fecha, ana, ReservationStatus.CONFIRMADA);
        User observador = crearUsuario("observador-van");

        mockMvc.perform(get("/experiences/" + expId).with(user(observador.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ana.getUsername())))
                .andExpect(content().string(containsString("personas ya reservaron")));
    }

    @Test
    void noMuestraReservasPendientesNiCanceladas() throws Exception {
        User duenio = crearUsuario("duenio-van2");
        Long expId = crearExperiencia(duenio);
        ExperienceInstance fecha = instanciaFutura(expId);
        User pendiente = crearUsuario("pendiente");
        reservar(fecha, pendiente, ReservationStatus.PENDIENTE);
        User cancelada = crearUsuario("cancelada");
        reservar(fecha, cancelada, ReservationStatus.CANCELADO);
        // Una CONFIRMADA para que la seccion exista y se pueda contrastar.
        User confirmada = crearUsuario("confirmada");
        reservar(fecha, confirmada, ReservationStatus.CONFIRMADA);
        User observador = crearUsuario("observador-van2");

        mockMvc.perform(get("/experiences/" + expId).with(user(observador.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(confirmada.getUsername())))
                .andExpect(content().string(not(containsString(pendiente.getUsername()))))
                .andExpect(content().string(not(containsString(cancelada.getUsername()))));
    }

    @Test
    void sinReservasNoApareceLaSeccion() throws Exception {
        User duenio = crearUsuario("duenio-van3");
        Long expId = crearExperiencia(duenio);
        instanciaFutura(expId);
        User observador = crearUsuario("observador-van3");

        mockMvc.perform(get("/experiences/" + expId).with(user(observador.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("personas ya reservaron"))));
    }

    private void reservar(ExperienceInstance fecha, User quien, ReservationStatus estado) {
        reservationRepository.save(Reservation.builder()
                .instance(fecha)
                .user(quien)
                .attendees(1)
                .status(estado)
                .build());
    }

    private ExperienceInstance instanciaFutura(Long expId) {
        return instanceRepository.findByExperienceIdOrderByDateAsc(expId).get(0);
    }

    private Long crearExperiencia(User duenio) {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                "Exp Van " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
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
