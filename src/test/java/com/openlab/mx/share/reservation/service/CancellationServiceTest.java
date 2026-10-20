package com.openlab.mx.share.reservation.service;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.service.NotificationService;
import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancellationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.openlab.mx.share.payment.gateway.PaymentGateway paymentGateway;
    /** Política real (no mock): los tramos son parte del comportamiento probado. */
    @Spy
    private RefundPolicy refundPolicy = new RefundPolicy("24:0.90,0:0.50");

    @InjectMocks
    private CancellationService cancellationService;

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private ExperienceInstance instance(Long id, LocalDate date, LocalTime startTime) {
        Experience experience = Experience.builder()
                .id(1L)
                .creator(user(1L))
                .name("Tour")
                .price(BigDecimal.valueOf(190))
                .minCapacity(2)
                .maxCapacity(8)
                .build();
        return ExperienceInstance.builder()
                .id(id)
                .experience(experience)
                .date(date)
                .startTime(startTime)
                .endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED)
                .build();
    }

    private Reservation reserva(Long id, ExperienceInstance instance, Long userId) {
        return Reservation.builder()
                .id(id)
                .instance(instance)
                .user(user(userId))
                .attendees(2)
                .status(ReservationStatus.CONFIRMADA)
                .build();
    }

    private Payment pago() {
        return pago("pi_cancel_1");
    }

    private Payment pago(String paymentIntentId) {
        return Payment.builder()
                .id(50L)
                .amount(new BigDecimal("380.00"))
                .commission(new BigDecimal("19.00"))
                .status(PaymentStatus.HELD)
                .heldAt(LocalDateTime.now().minusDays(1))
                .stripePaymentIntentId(paymentIntentId)
                .build();
    }

    // ---------- Caso 1: cancela el participante ----------

    @Test
    void testCaso1LiberaCupoYNotificaAlCreador() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(10), LocalTime.of(10, 0));
        Reservation reserva = reserva(1L, instancia, 200L);
        Payment pago = pago();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));

        cancellationService.cancelByParticipant(1L, user(200L));

        assertEquals(ReservationStatus.CANCELADO, reserva.getStatus(), "cupo liberado al instante");
        verify(notificationService).notify(
                org.mockito.ArgumentMatchers.argThat(u -> u != null && Long.valueOf(1L).equals(u.getId())),
                eq(NotificationType.CANCELACION_REEMBOLSO), anyString(), anyString());
    }

    @Test
    void testCaso1Reembolsa90YRetiene10MasDe24h() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(10), LocalTime.of(10, 0));
        Reservation reserva = reserva(1L, instancia, 200L);
        Payment pago = pago();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));

        cancellationService.cancelByParticipant(1L, user(200L));

        assertEquals(PaymentStatus.REFUNDED_PARTIAL, pago.getStatus());
        assertEquals(0, pago.getRefund().compareTo(new BigDecimal("342.00")),
                "con más de 24h se devuelve el 90% de 380");
        // capture parcial = la plataforma retiene el 10% (spec §4)
        verify(paymentGateway).capturePartial("pi_cancel_1", new BigDecimal("38.00"));
        verify(paymentGateway, never()).cancel(any());
    }

    @Test
    void testCaso1ReembolsoParcialMenosDe24h() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(1), LocalTime.MIDNIGHT);
        Reservation reserva = reserva(1L, instancia, 200L);
        Payment pago = pago();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));

        cancellationService.cancelByParticipant(1L, user(200L));

        assertEquals(PaymentStatus.REFUNDED_PARTIAL, pago.getStatus());
        assertEquals(0, pago.getRefund().compareTo(new BigDecimal("190.00")),
                "parcial = amount * 0.5");
        // refund parcial = capture del 50%: la penalidad queda en la plataforma (spec §4)
        verify(paymentGateway).capturePartial("pi_cancel_1", new BigDecimal("190.00"));
    }

    /** Pago legacy (sin pi): refund contable, gateway no invocado (spec §11). */
    @Test
    void testCaso1LegacySinPiNoInvocaGateway() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(10), LocalTime.of(10, 0));
        Reservation reserva = reserva(1L, instancia, 200L);
        Payment pago = pago(null);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));

        cancellationService.cancelByParticipant(1L, user(200L));

        assertEquals(PaymentStatus.REFUNDED_PARTIAL, pago.getStatus());
        assertEquals(0, pago.getRefund().compareTo(new BigDecimal("342.00")));
        verify(paymentGateway, never()).cancel(any());
        verify(paymentGateway, never()).capturePartial(any(), any());
    }

    @Test
    void testNoPuedeCancelarReservaAjena() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(10), LocalTime.of(10, 0));
        Reservation reserva = reserva(1L, instancia, 200L);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reserva));

        assertThrows(NotOwnerException.class,
                () -> cancellationService.cancelByParticipant(1L, user(999L)));
        assertEquals(ReservationStatus.CONFIRMADA, reserva.getStatus(), "no se muta");
        verify(paymentRepository, never()).findByReservationId(any());
    }

    // ---------- Caso 2: cancela el creador ----------

    @Test
    void testCaso2NotificaMasivaYRefundTotalYArchivaChat() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(10), LocalTime.of(10, 0));
        Reservation r1 = reserva(1L, instancia, 200L);
        Reservation r2 = reserva(2L, instancia, 300L);
        Payment p1 = pago();
        Payment p2 = pago();
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia));
        when(reservationRepository.findByInstanceIdAndStatusNot(10L, ReservationStatus.CANCELADO))
                .thenReturn(List.of(r1, r2));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(p1));
        when(paymentRepository.findByReservationId(2L)).thenReturn(Optional.of(p2));
        when(notificationService.notifyMassive(anyList(), eq(NotificationType.CANCELACION_REEMBOLSO),
                anyString(), anyString())).thenReturn(2);

        cancellationService.cancelByCreator(10L, user(1L));

        assertEquals(InstanceStatus.CANCELLED, instancia.getStatus());
        assertTrue(instancia.isArchived(), "el chat queda archivado");
        assertEquals(ReservationStatus.CANCELADO, r1.getStatus());
        assertEquals(ReservationStatus.CANCELADO, r2.getStatus());
        assertEquals(PaymentStatus.REFUNDED_FULL, p1.getStatus());
        assertEquals(PaymentStatus.REFUNDED_FULL, p2.getStatus());
        assertEquals(0, p1.getRefund().compareTo(p1.getAmount()));
        verify(notificationService).notifyMassive(anyList(),
                eq(NotificationType.CANCELACION_REEMBOLSO), anyString(), anyString());
    }

    @Test
    void testCaso2RechazaSiNoEsDueno() {
        ExperienceInstance instancia = instance(10L, LocalDate.now().plusDays(10), LocalTime.of(10, 0));
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia));

        assertThrows(NotOwnerException.class,
                () -> cancellationService.cancelByCreator(10L, user(999L)));
        assertEquals(InstanceStatus.SCHEDULED, instancia.getStatus());
    }

    // ---------- Caso 3: cupo mínimo ----------

    @Test
    void testCaso3IgualQueCaso2() {
        ExperienceInstance instancia = instance(10L, LocalDate.now(), LocalTime.of(10, 0));
        Reservation r1 = reserva(1L, instancia, 200L);
        Payment p1 = pago();
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia));
        when(reservationRepository.findByInstanceIdAndStatusNot(10L, ReservationStatus.CANCELADO))
                .thenReturn(List.of(r1));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(p1));
        when(notificationService.notifyMassive(anyList(), eq(NotificationType.CANCELACION_REEMBOLSO),
                anyString(), anyString())).thenReturn(1);

        cancellationService.cancelByMinCapacity(10L);

        assertEquals(InstanceStatus.CANCELLED, instancia.getStatus());
        assertTrue(instancia.isArchived());
        assertEquals(ReservationStatus.CANCELADO, r1.getStatus());
        assertEquals(PaymentStatus.REFUNDED_FULL, p1.getStatus());
        assertEquals(0, p1.getRefund().compareTo(p1.getAmount()));
        verify(notificationService).notifyMassive(anyList(),
                eq(NotificationType.CANCELACION_REEMBOLSO), anyString(), anyString());
    }
}
