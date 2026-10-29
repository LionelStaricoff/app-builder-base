package com.openlab.mx.movo.payment.repository;

import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.entity.PayoutStatus;
import com.openlab.mx.movo.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReservationId(Long reservationId);

    Optional<Payment> findFirstByStripeSessionId(String sessionId);

    Optional<Payment> findFirstByStripePaymentIntentId(String paymentIntentId);

    List<Payment> findByStatus(PaymentStatus status);

    /** Pagos retenidos de instancias que ya cumplieron la ventana de escrow. */
    List<Payment> findByStatusAndHeldAtBefore(PaymentStatus status, LocalDateTime heldAtBefore);

    /** Pagos RELEASED con payout pendiente para un creador específico. */
    @Query("SELECT p FROM Payment p " +
           "JOIN p.reservation r " +
           "JOIN r.instance i " +
           "JOIN i.experience e " +
           "WHERE e.creator = :creator " +
           "AND p.status = com.openlab.mx.movo.payment.entity.PaymentStatus.RELEASED " +
           "AND p.payoutStatus = com.openlab.mx.movo.payment.entity.PayoutStatus.PENDING")
    List<Payment> findPendingPayoutsByCreator(@Param("creator") User creator);
}
