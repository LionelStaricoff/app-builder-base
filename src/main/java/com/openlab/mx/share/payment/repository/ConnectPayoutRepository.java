package com.openlab.mx.share.payment.repository;

import com.openlab.mx.share.payment.entity.ConnectPayout;
import com.openlab.mx.share.payment.entity.PayoutStatus;
import com.openlab.mx.share.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectPayoutRepository extends JpaRepository<ConnectPayout, Long> {

    List<ConnectPayout> findByCreatorAndStatus(User creator, PayoutStatus status);

    List<ConnectPayout> findByCreatorOrderByRequestedAtDesc(User creator);

    Optional<ConnectPayout> findByIdempotencyKey(String idempotencyKey);

    boolean existsByPaymentId(Long paymentId);
}
