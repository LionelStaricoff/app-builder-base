package com.openlab.mx.share.payment.repository;

import com.openlab.mx.share.payment.entity.Payment;
import com.openlab.mx.share.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReservationId(Long reservationId);

    Optional<Payment> findFirstByStripeSessionId(String stripeSessionId);

    List<Payment> findByStatus(PaymentStatus status);

    /** Pagos retenidos de instancias que ya cumplieron la ventana de escrow. */
    List<Payment> findByStatusAndHeldAtBefore(PaymentStatus status, LocalDateTime heldAtBefore);
}
