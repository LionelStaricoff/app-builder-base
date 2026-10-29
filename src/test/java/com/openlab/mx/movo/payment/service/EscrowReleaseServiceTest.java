package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscrowReleaseServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private EscrowService escrowService;

    @InjectMocks
    private EscrowReleaseService escrowReleaseService;

    private Reservation reserva(Long id) {
        return Reservation.builder()
                .id(id)
                .user(com.openlab.mx.movo.user.entity.User.builder().id(id).username("u" + id).build())
                .attendees(1)
                .status(ReservationStatus.CONFIRMADA)
                .build();
    }

    private Payment pago(PaymentStatus status, LocalDateTime heldAt) {
        return Payment.builder()
                .id(heldAt != null ? 1L : 2L)
                .amount(BigDecimal.valueOf(190))
                .commission(BigDecimal.valueOf(9.5))
                .status(status)
                .heldAt(heldAt)
                .build();
    }

    @Test
    void testLiberaSoloLosPagosVencidos() {
        Reservation vencida = reserva(1L);
        Reservation vigente = reserva(2L);
        when(reservationRepository.findByInstanceIdAndStatus(10L, ReservationStatus.CONFIRMADA))
                .thenReturn(List.of(vencida, vigente));
        when(paymentRepository.findByReservationId(1L))
                .thenReturn(Optional.of(pago(PaymentStatus.HELD, LocalDateTime.now().minusDays(3))));
        when(paymentRepository.findByReservationId(2L))
                .thenReturn(Optional.of(pago(PaymentStatus.HELD, LocalDateTime.now())));
        when(escrowService.tryRelease(1L)).thenReturn(true);

        int liberados = escrowReleaseService.releaseIfDue(10L);

        assertEquals(1, liberados);
        verify(escrowService).tryRelease(1L);
        verify(escrowService, never()).tryRelease(2L);
    }

    @Test
    void testNoTocaPagosNoHeld() {
        when(reservationRepository.findByInstanceIdAndStatus(10L, ReservationStatus.CONFIRMADA))
                .thenReturn(List.of(reserva(1L)));
        when(paymentRepository.findByReservationId(1L))
                .thenReturn(Optional.of(pago(PaymentStatus.REFUNDED_FULL, LocalDateTime.now().minusDays(5))));

        int liberados = escrowReleaseService.releaseIfDue(10L);

        assertEquals(0, liberados);
        verify(escrowService, never()).tryRelease(anyLong());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
