package com.openlab.mx.share.payment.repository;

import com.openlab.mx.share.payment.entity.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {
}
