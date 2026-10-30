package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.payment.service.InstanceConfirmedReservationsPort;
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

    private final InstanceConfirmedReservationsPort instanceReservationsPort;
    private final PaymentRepository paymentRepository;
    private final EscrowService escrowService;

    @Value("${escrow.auto-release-days}")
    private long autoReleaseDays = 2;

    /** @return cuántas liberaciones ocurrieron en esta corrida. */
    @Transactional
    public int releaseIfDue(Long instanceId) {
        List<Long> confirmadas = instanceReservationsPort.confirmedReservationIds(instanceId);
        LocalDateTime corte = LocalDateTime.now(ZoneId.systemDefault()).minusDays(autoReleaseDays);
        int liberados = 0;
        for (Long reservationId : confirmadas) {
            Payment payment = paymentRepository.findByReservationId(reservationId).orElse(null);
            if (payment == null
                    || payment.getStatus() != PaymentStatus.HELD
                    || payment.getHeldAt() == null
                    || payment.getHeldAt().isAfter(corte)) {
                continue;
            }
            if (escrowService.tryRelease(reservationId)) {
                liberados++;
            }
        }
        return liberados;
    }
}
