package com.openlab.mx.movo.payment.repository;

import com.openlab.mx.movo.payment.entity.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {
}
