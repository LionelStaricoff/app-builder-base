package com.openlab.mx.movo.payment.repository;

import com.openlab.mx.movo.payment.entity.ConnectAccount;
import com.openlab.mx.movo.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnectAccountRepository extends JpaRepository<ConnectAccount, Long> {

    Optional<ConnectAccount> findByUser(User user);

    Optional<ConnectAccount> findByStripeAccountId(String stripeAccountId);

    boolean existsByUser(User user);
}
