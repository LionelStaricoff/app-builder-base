package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import com.openlab.mx.share.payment.repository.PaymentRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;import java.time.LocalDateTime;
import java.util.List;

/**
 * Liberación programada (spec §8): pagos con `heldAt + escrow.auto-release-days`
 * vencidos se liberan aunque nadie haya evaluado. Idempotente.
 */
@Service
@RequiredArgsConstructor
public class EscrowReleaseService {

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final EscrowService escrowService;

    @Value("${escrow.auto-release-days}")
    private long autoReleaseDays = 2;

    /** @return cuántas liberaciones ocurrieron en esta corrida. */
    @Transactional
    public int releaseIfDue(Long instanceId) {
        List<Reservation> confirmadas =
                reservationRepository.findByInstanceIdAndStatus(instanceId, ReservationStatus.CONFIRMADA);
        LocalDateTime corte = LocalDateTime.now(ZoneId.systemDefault()).minusDays(autoReleaseDays);
        int liberados = 0;
        for (Reservation reservation : confirmadas) {
            Payment payment = paymentRepository.findByReservationId(reservation.getId()).orElse(null);
            if (payment == null
                    || payment.getStatus() != PaymentStatus.HELD
                    || payment.getHeldAt() == null
                    || payment.getHeldAt().isAfter(corte)) {
                continue;
            }
            if (escrowService.tryRelease(reservation.getId())) {
                liberados++;
            }
        }
        return liberados;
    }
}
