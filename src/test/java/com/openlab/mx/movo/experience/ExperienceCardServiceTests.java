package com.openlab.mx.movo.experience;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.service.ExperienceCardService;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.shared.TestIdentityGateConfig;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestIdentityGateConfig.class)
class ExperienceCardServiceTests {

    @Autowired
    private ExperienceCardService cardService;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private ExperienceInstanceRepository instanceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    private User usuario(String rol, String avatar) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username("u_" + sufijo)
                .email("u_" + sufijo + "@test.local")
                .passwordHash("x")
                .birthDate(LocalDate.of(1990, 1, 1))
                .avatarBase64(avatar)
                .role(Role.valueOf(rol))
                .build());
    }

    private Experience experiencia(User creador) {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        return experienceService.create(new ExperienceFormRequest(
                "Card Exp " + UUID.randomUUID(), "Desc", 120, 1, 6, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(
                        new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null),
                        new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null),
                creador);
    }

    private void reservar(ExperienceInstance instancia, User usuario, int cupos,
                          ReservationStatus estado) {
        reservationRepository.save(Reservation.builder()
                .instance(instancia)
                .user(usuario)
                .attendees(cupos)
                .status(estado)
                .build());
    }

    @Test
    void devuelveCuposYAvataresPorFechaSinContarCanceladas() {
        User creador = usuario("CREATOR", null);
        Experience experience = experiencia(creador);
        List<ExperienceInstance> instancias =
                instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId());
        assertEquals(2, instancias.size());
        ExperienceInstance primera = instancias.get(0);
        ExperienceInstance segunda = instancias.get(1);

        User ana = usuario("USER", "avatar-ana");
        User beto = usuario("USER", "avatar-beto");
        reservar(primera, ana, 2, ReservationStatus.PENDIENTE);
        reservar(primera, beto, 1, ReservationStatus.CONFIRMADA);
        reservar(segunda, ana, 1, ReservationStatus.CONFIRMADA);
        // Cancelada: libera cupo y no aporta avatar.
        reservar(segunda, beto, 4, ReservationStatus.CANCELADO);

        var fechas = cardService.dates(experience.getId());

        assertEquals(2, fechas.size());
        assertEquals(primera.getId(), fechas.get(0).instanceId());
        assertEquals(3, fechas.get(0).reservedCount());
        assertEquals(6 - 3, fechas.get(0).availableCapacity());
        assertEquals(List.of("avatar-ana", "avatar-beto"), fechas.get(0).reservedAvatars());
        assertEquals(90, fechas.get(0).refundPercent());

        assertEquals(segunda.getId(), fechas.get(1).instanceId());
        assertEquals(1, fechas.get(1).reservedCount());
        assertEquals(6 - 1, fechas.get(1).availableCapacity());
        assertEquals(List.of("avatar-ana"), fechas.get(1).reservedAvatars());
    }

    @Test
    void ignoraFechasCanceladasOPasadas() {
        User creador = usuario("CREATOR", null);
        Experience experience = experiencia(creador);
        instanceRepository.save(ExperienceInstance.builder()
                .experience(experience)
                .date(LocalDate.now().plusDays(40))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .status(InstanceStatus.CANCELLED)
                .build());
        instanceRepository.save(ExperienceInstance.builder()
                .experience(experience)
                .date(LocalDate.now().minusDays(2))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .status(InstanceStatus.SCHEDULED)
                .build());

        var fechas = cardService.dates(experience.getId());

        assertEquals(2, fechas.size());
        assertTrue(fechas.stream().allMatch(f -> f.date().isAfter(LocalDate.now())));
    }

    @Test
    void availabilityRechazaUnaFechaDeOtraExperiencia() {
        User creador = usuario("CREATOR", null);
        Experience una = experiencia(creador);
        Experience otra = experiencia(creador);
        Long fechaDeOtra = instanceRepository.findByExperienceIdOrderByDateAsc(otra.getId())
                .get(0).getId();

        assertThrows(DomainException.class,
                () -> cardService.availability(una.getId(), fechaDeOtra));
    }

    @Test
    void availabilityDevuelveLaDisponibilidadDeEsaFecha() {
        User creador = usuario("CREATOR", null);
        Experience experience = experiencia(creador);
        ExperienceInstance primera =
                instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId()).get(0);
        reservar(primera, usuario("USER", "avatar-uno"), 2, ReservationStatus.CONFIRMADA);

        var fecha = cardService.availability(experience.getId(), primera.getId());

        assertEquals(primera.getId(), fecha.instanceId());
        assertEquals(2, fecha.reservedCount());
        assertEquals(4, fecha.availableCapacity());
    }
}
