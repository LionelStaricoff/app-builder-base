package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.repository.CommentRepository;
import com.openlab.mx.movo.experience.repository.RatingRepository;
import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.payment.service.ReservationStatusPort;
import com.openlab.mx.movo.shared.exception.EscrowException;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class EscrowServiceTest {

    @Mock
    private ReservationStatusPort reservationStatusPort;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private com.openlab.mx.movo.notification.service.NotificationService notificationService;
    @Mock
    private com.openlab.mx.movo.payment.gateway.PaymentGateway paymentGateway;

    @Mock
    private com.openlab.mx.movo.payment.service.EscrowExperiencePort experiencePort;

    @InjectMocks
    private EscrowService escrowService;

    private ExperienceInstance instance(InstanceStatus status) {
        Experience experience = Experience.builder()
                .id(1L)
                .creator(User.builder().id(1L).username("creador").build())
                .name("Tour")
                .price(BigDecimal.valueOf(190))
                .minCapacity(1)
                .maxCapacity(8)
                .build();
        return ExperienceInstance.builder()
                .id(10L)
                .experience(experience)
                .date(LocalDate.of(2026, 12, 1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .status(status)
                .build();
    }

    private Reservation reservation(Long id, Long userId, ExperienceInstance instance) {
        return Reservation.builder()
                .id(id)
                .instance(instance)
                .user(User.builder().id(userId).username("u" + userId).build())
                .attendees(2)
                .status(ReservationStatus.CONFIRMADA)
                .build();
    }

    private Payment payment(PaymentStatus status, LocalDateTime heldAt) {
        return payment(status, heldAt, null);
    }

    private Payment payment(PaymentStatus status, LocalDateTime heldAt, String paymentIntentId) {
        return Payment.builder()
                .id(50L)
                .amount(new BigDecimal("380.00"))
                .commission(new BigDecimal("19.00"))
                .status(status)
                .heldAt(heldAt)
                .stripePaymentIntentId(paymentIntentId)
                .build();
    }

    @Test
    void testLiberaCuandoTodosEvaluan() {
        ExperienceInstance instancia = instance(InstanceStatus.COMPLETED);
        Reservation r1 = reservation(1L, 100L, instancia);
        Reservation r2 = reservation(2L, 101L, instancia);
        Payment pago = payment(PaymentStatus.HELD, LocalDateTime.now(), "pi_escrow_1");

        when(reservationStatusPort.confirmedInstanceId(1L)).thenReturn(Optional.of(10L));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));
        when(experiencePort.isInstanceCompleted(10L)).thenReturn(true);
        when(experiencePort.allConfirmedRated(10L)).thenReturn(true);
        when(experiencePort.experienceCreator(10L)).thenReturn(Optional.of(
                com.openlab.mx.movo.user.entity.User.builder().id(1L).username("creador").build()));
        when(experiencePort.experienceName(10L)).thenReturn("Tour");

        boolean liberado = escrowService.tryRelease(1L);

        assertTrue(liberado);
        // release de escrow captura el pago en Stripe (spec §4)
        verify(paymentGateway).capture("pi_escrow_1");
        assertEquals(PaymentStatus.RELEASED, pago.getStatus());
        assertEquals(0, pago.getPayout().compareTo(new BigDecimal("361.00")),
                "payout = amount - commission (95%)");
        verify(paymentRepository).save(pago);
        verify(notificationService).notify(
                org.mockito.ArgumentMatchers.argThat(u -> u != null && Long.valueOf(1L).equals(u.getId())),
                eq(com.openlab.mx.movo.notification.entity.NotificationType.PAGO_LIBERADO),
                anyString(), anyString());
    }

    @Test
    void testNoLiberaSiFaltaEvaluacion() {
        ExperienceInstance instancia = instance(InstanceStatus.COMPLETED);
        Reservation r1 = reservation(1L, 100L, instancia);
        Reservation r2 = reservation(2L, 101L, instancia);
        Payment pago = payment(PaymentStatus.HELD, LocalDateTime.now());

        when(reservationStatusPort.confirmedInstanceId(1L)).thenReturn(Optional.of(10L));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));
        when(ratingRepository.findByInstanceId(10L))
                .thenReturn(List.of(com.openlab.mx.movo.experience.entity.Rating.builder()
                        .instance(instancia).user(r1.getUser()).stars(5).build()));
        when(commentRepository.findByInstanceId(10L))
                .thenReturn(List.of(com.openlab.mx.movo.experience.entity.Comment.builder()
                        .instance(instancia).user(r1.getUser()).text("Genial").build()));

        boolean liberado = escrowService.tryRelease(1L);

        assertFalse(liberado);
        assertEquals(PaymentStatus.HELD, pago.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testLiberaPasan2Dias() {
        ExperienceInstance instancia = instance(InstanceStatus.COMPLETED);
        Reservation r1 = reservation(1L, 100L, instancia);
        Payment pago = payment(PaymentStatus.HELD, LocalDateTime.now().minusDays(3));

        when(reservationStatusPort.confirmedInstanceId(1L)).thenReturn(Optional.of(10L));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));
        when(experiencePort.isInstanceCompleted(10L)).thenReturn(true);
        when(experiencePort.allConfirmedRated(10L)).thenReturn(false);

        boolean liberado = escrowService.tryRelease(1L);

        assertTrue(liberado, "pasaron los 2 días: se libera aunque nadie evalúe");
        assertEquals(PaymentStatus.RELEASED, pago.getStatus());
    }

    @Test
    void testReembolsoCanceladoNoSeLibera() {
        ExperienceInstance instancia = instance(InstanceStatus.COMPLETED);
        Reservation r1 = reservation(1L, 100L, instancia);
        Payment pago = payment(PaymentStatus.REFUNDED_FULL, LocalDateTime.now().minusDays(5));

        when(reservationStatusPort.confirmedInstanceId(1L)).thenReturn(Optional.of(10L));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));

        assertThrows(EscrowException.class, () -> escrowService.tryRelease(1L));
        assertEquals(PaymentStatus.REFUNDED_FULL, pago.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    /** Pago legacy simulado (sin pi): el gateway no se invoca (spec §11). */
    @Test
    void testPagoLegacySinPiNoInvocaGateway() {
        ExperienceInstance instancia = instance(InstanceStatus.COMPLETED);
        Reservation r1 = reservation(1L, 100L, instancia);
        Payment pago = payment(PaymentStatus.HELD, LocalDateTime.now().minusDays(3));

        when(reservationStatusPort.confirmedInstanceId(1L)).thenReturn(Optional.of(10L));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));
        when(experiencePort.isInstanceCompleted(10L)).thenReturn(true);
        when(experiencePort.allConfirmedRated(10L)).thenReturn(false);

        assertTrue(escrowService.tryRelease(1L));
        assertEquals(PaymentStatus.RELEASED, pago.getStatus());
        verify(paymentGateway, never()).capture(any());
        verify(paymentGateway, never()).cancel(any());
        verify(paymentGateway, never()).capturePartial(any(), any());
    }

    @Test
    void testLiberacionIdempotente() {
        ExperienceInstance instancia = instance(InstanceStatus.COMPLETED);
        Reservation r1 = reservation(1L, 100L, instancia);
        Payment pago = payment(PaymentStatus.HELD, LocalDateTime.now());

        when(reservationStatusPort.confirmedInstanceId(1L)).thenReturn(Optional.of(10L));
        when(paymentRepository.findByReservationId(1L)).thenReturn(Optional.of(pago));
        when(experiencePort.isInstanceCompleted(10L)).thenReturn(true);
        when(experiencePort.allConfirmedRated(10L)).thenReturn(true);
        when(experiencePort.experienceCreator(10L)).thenReturn(Optional.of(
                com.openlab.mx.movo.user.entity.User.builder().id(1L).username("creador").build()));
        when(experiencePort.experienceName(10L)).thenReturn("Tour");

        assertTrue(escrowService.tryRelease(1L));
        assertEquals(PaymentStatus.RELEASED, pago.getStatus());
        BigDecimal payout = pago.getPayout();

        assertFalse(escrowService.tryRelease(1L), "segunda corrida: no-op");
        assertEquals(PaymentStatus.RELEASED, pago.getStatus());
        assertEquals(0, pago.getPayout().compareTo(payout));
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }
}
