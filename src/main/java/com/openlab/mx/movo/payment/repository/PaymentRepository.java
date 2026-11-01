package com.openlab.mx.movo.payment.repository;

import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRideId(Long rideId);

    List<Payment> findByCreatorId(Long creatorId);

    List<Payment> findByCreatorIdAndPayoutStatus(Long creatorId, PayoutStatus payoutStatus);

    Optional<Payment> findFirstByStripeSessionId(String sessionId);

    Optional<Payment> findFirstByStripePaymentIntentId(String paymentIntentId);

    List<Payment> findByStatus(PaymentStatus status);

    /** Pagos retenidos de instancias que ya cumplieron la ventana de escrow. */
    List<Payment> findByStatusAndHeldAtBefore(PaymentStatus status, LocalDateTime heldAtBefore);

}
