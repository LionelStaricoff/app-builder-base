package com.openlab.mx.movo.reservation;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.shared.TestIdentityGateConfig;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Salidas post-pago en /confirmation: enlace al chat grupal y descarga
 * del calendario (.ics).
 *
 * OJO con el chat: tras pagar la reserva queda PENDIENTE y el chat recién
 * se abre cuando el creador la confirma (ChatAuthorizationService exige
 * CONFIRMADA), así que el enlace solo se muestra cuando de verdad funciona.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityGateConfig.class)
class PostPagoConfirmationTests {

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
    void confirmadaMuestraChatYCalendario() throws Exception {
        User creador = usuario("creador");
        User huesped = usuario("huesped");
        Long expId = crearExperiencia(creador, "Exp Chat " + corto());
        ExperienceInstance fecha = crearFecha(expId);
        Reservation reserva = reservar(fecha, huesped, ReservationStatus.CONFIRMADA);

        mockMvc.perform(get("/confirmation").with(user(huesped.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/chats/" + fecha.getId())))
                .andExpect(content().string(containsString(
                        "/reservations/" + reserva.getId() + "/calendar.ics")));
    }

    @Test
    void pendienteMuestraAvisoSinLinkDeChat() throws Exception {
        User creador = usuario("creador");
        User huesped = usuario("huesped");
        Long expId = crearExperiencia(creador, "Exp Aviso " + corto());
        ExperienceInstance fecha = crearFecha(expId);
        reservar(fecha, huesped, ReservationStatus.PENDIENTE);

        mockMvc.perform(get("/confirmation").with(user(huesped.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/chats/" + fecha.getId()))))
                .andExpect(content().string(containsString("cuando se complete el pago")))
                .andExpect(content().string(not(containsString(creador.getUsername()))))
                .andExpect(content().string(containsString("calendar.ics")));
    }

    @Test
    void icsDescargaElEventoDeLaFecha() throws Exception {
        User creador = usuario("creador");
        User huesped = usuario("huesped");
        String nombre = "Exp Calendario " + corto();
        Long expId = crearExperiencia(creador, nombre);
        ExperienceInstance fecha = crearFecha(expId);
        Reservation reserva = reservar(fecha, huesped, ReservationStatus.CONFIRMADA);

        mockMvc.perform(get("/reservations/" + reserva.getId() + "/calendar.ics")
                        .with(user(huesped.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "calendar")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("BEGIN:VEVENT")))
                .andExpect(content().string(containsString(
                        "DTSTART:" + fecha.getDate().toString().replace("-", "") + "T190000")))
                .andExpect(content().string(containsString("DTEND:" + fecha.getDate().toString().replace("-", "") + "T213000")))
                .andExpect(content().string(containsString(nombre)));
    }

    @Test
    void icsDeOtraPersonaEs403() throws Exception {
        User creador = usuario("creador");
        User huesped = usuario("huesped");
        User intruso = usuario("intruso");
        Long expId = crearExperiencia(creador, "Exp 403 " + corto());
        ExperienceInstance fecha = crearFecha(expId);
        Reservation reserva = reservar(fecha, huesped, ReservationStatus.CONFIRMADA);

        mockMvc.perform(get("/reservations/" + reserva.getId() + "/calendar.ics")
                        .with(user(intruso.getUsername())))
                .andExpect(status().isForbidden());
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User usuario(String prefijo) {
        String base = prefijo + "-" + corto();
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

    private Long crearExperiencia(User creador, String nombre) {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                nombre, "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), creador).getId();
    }

    /** Fecha futura 19:00–21:30: los extremos que tiene que reflejar el .ics. */
    private ExperienceInstance crearFecha(Long expId) {
        return instanceRepository.save(ExperienceInstance.builder()
                .experience(experienceService.get(expId))
                .date(LocalDate.now().plusDays(15))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 30))
                .status(InstanceStatus.SCHEDULED)
                .build());
    }

    private Reservation reservar(ExperienceInstance fecha, User u, ReservationStatus status) {
        return reservationRepository.save(Reservation.builder()
                .instance(fecha)
                .user(u)
                .attendees(2)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
