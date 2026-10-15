package com.openlab.mx.share.identity.repository;

import com.openlab.mx.share.identity.entity.DiditWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiditWebhookEventRepository extends JpaRepository<DiditWebhookEvent, String> {
}
