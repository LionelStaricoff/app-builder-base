package com.openlab.mx.share.reservation.service;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.identity.service.RequireVerifiedIdentityService;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.CupoAgotadoException;
import com.openlab.mx.share.shared.exception.InstancePastException;
import com.openlab.mx.share.shared.exception.UnverifiedIdentityException;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RequireVerifiedIdentityService requireVerifiedIdentityService;

    /**
     * Reloj congelado: 2026-10-20 09:00 hora de Ciudad de México (15:00 UTC).
     * Con el reloj real los tests dependían de la hora del día (uno fallaba
     * entre las 21:00 y las 22:00, cuando "ahora + 2 h" ya había cruzado la
     * medianoche).
     */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Mexico_City"));

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                instanceRepository, reservationRepository, requireVerifiedIdentityService, RELOJ);
    }

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private ExperienceInstance instance(Long id, int maxCapacity, BigDecimal price, LocalDate date) {
        Experience experience = Experience.builder()
                .id(1L)
                .creator(user(1L))
                .name("Tour")
                .price(price)
                .minCapacity(1)
                .maxCapacity(maxCapacity)
                .build();
        return ExperienceInstance.builder()
                .id(id)
                .experience(experience)
                .date(date)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED)
                .build();
    }

    private void stubGuardar() {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
    }

    /**
     * Spec §5-6: reservar ya no retiene pago (HELD); el Payment lo crea
     * StripeCheckoutService al armar la Checkout Session.
     */
    @Test
    void testReservaSinIdentidadVerificadaSeBloquea() {
        org.mockito.Mockito.doThrow(new UnverifiedIdentityException())
                .when(requireVerifiedIdentityService).requireVerified(2L);

        assertThrows(UnverifiedIdentityException.class,
                () -> reservationService.reserve(5L, 1, user(2L)));

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testReservaHappyPathSoloCreaLaReserva() {
        when(instanceRepository.findById(5L)).thenReturn(
                Optional.of(instance(5L, 8, BigDecimal.valueOf(190), LocalDate.now(RELOJ).plusDays(10))));
        when(reservationRepository.countAttendees(anyLong())).thenReturn(0L);
        stubGuardar();

        Reservation reservation = reservationService.reserve(5L, 2, user(2L));

        assertEquals(ReservationStatus.PENDIENTE, reservation.getStatus());
        assertEquals(2, reservation.getAttendees());
    }

    @Test
    void testRechazaCupoAgotado() {
        when(instanceRepository.findById(5L)).thenReturn(
                Optional.of(instance(5L, 1, BigDecimal.valueOf(100), LocalDate.now(RELOJ).plusDays(10))));
        when(reservationRepository.countAttendees(anyLong())).thenReturn(0L, 1L);

        reservationService.reserve(5L, 1, user(2L));

        assertThrows(CupoAgotadoException.class,
                () -> reservationService.reserve(5L, 1, user(3L)));
    }

    @Test
    void testRechazaInstanciaPasada() {
        when(instanceRepository.findById(5L)).thenReturn(
                Optional.of(instance(5L, 8, BigDecimal.valueOf(190), LocalDate.now(RELOJ).minusDays(1))));

        assertThrows(InstancePastException.class,
                () -> reservationService.reserve(5L, 1, user(2L)));
    }

    @Test
    void unaExperienciaQueTerminaDespuesDeMedianocheNoEstaPasada() {
        // El reloj está en 2026-10-20 09:00. La experiencia empieza HOY a las
        // 23:30 y termina a las 00:30 del día siguiente: su endTime (00:30) es
        // menor que la hora actual (09:00), así que el código viejo la daba
        // por terminada y el checkout rebotaba con "Esa fecha ya pasó".
        ExperienceInstance instance = instance(5L, 8, BigDecimal.valueOf(190), LocalDate.now(RELOJ));
        instance.setStartTime(LocalTime.of(23, 30));
        instance.setEndTime(LocalTime.of(0, 30));
        when(instanceRepository.findById(5L)).thenReturn(Optional.of(instance));
        when(reservationRepository.countAttendees(anyLong())).thenReturn(0L);
        stubGuardar();

        Reservation reserva = reservationService.reserve(5L, 1, user(2L));

        assertEquals(ReservationStatus.PENDIENTE, reserva.getStatus());
    }

    @Test
    void unaExperienciaQueYaEmpezoNoSePuedeReservar() {
        // Hoy 09:00; la experiencia empezó a las 08:00 y termina a las 12:00.
        ExperienceInstance instance = instance(5L, 8, BigDecimal.valueOf(190), LocalDate.now(RELOJ));
        instance.setStartTime(LocalTime.of(8, 0));
        instance.setEndTime(LocalTime.of(12, 0));
        when(instanceRepository.findById(5L)).thenReturn(Optional.of(instance));

        assertThrows(InstancePastException.class,
                () -> reservationService.reserve(5L, 1, user(2L)));
    }
}
