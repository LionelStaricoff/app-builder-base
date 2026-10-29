package com.openlab.mx.movo.identity.repository;

import com.openlab.mx.movo.identity.entity.DiditWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiditWebhookEventRepository extends JpaRepository<DiditWebhookEvent, String> {
}
