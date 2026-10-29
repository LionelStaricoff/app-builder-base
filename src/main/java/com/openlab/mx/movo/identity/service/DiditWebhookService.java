package com.openlab.mx.movo.identity.service;

import com.openlab.mx.movo.identity.dto.WebhookDelivery;
import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.entity.DiditWebhookEvent;
import com.openlab.mx.movo.identity.entity.VerificationStatus;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.identity.repository.DiditWebhookEventRepository;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manejo del webhook de Didit: exige event_id, idempotencia por
 * {@code didit_webhook_events}, busca la fila por {@code vendor_data}
 * (userId) con fallback a session_id, aplica la decisión e ignora
 * literales de estado futuros sin romper (forward-compatible).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiditWebhookService {

    private final DiditVerificationRepository verificationRepository;
    private final DiditWebhookEventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public WebhookOutcome handle(WebhookDelivery delivery) {
        if (delivery.eventId() == null || delivery.eventId().isBlank()) {
            throw new InvalidWebhookPayloadException("Webhook sin event_id");
        }
        if (eventRepository.existsById(delivery.eventId())) {
            log.debug("Webhook duplicado ignorado: {}", delivery.eventId());
            return WebhookOutcome.DUPLICATE;
        }

        WebhookOutcome outcome = aplicarDecision(delivery);

        eventRepository.save(DiditWebhookEvent.builder()
                .eventId(delivery.eventId())
                .build());
        return outcome;
    }

    private WebhookOutcome aplicarDecision(WebhookDelivery delivery) {
        if (!VerificationStatus.isKnown(delivery.diditStatus())) {
            log.debug("Webhook con literal de estado desconocido (ignorado): {}",
                    delivery.diditStatus());
            return WebhookOutcome.IGNORED;
        }

        DiditVerification verification = buscar(delivery);
        if (verification == null) {
            log.warn("Webhook sin fila de verificación para session={} vendor_data={} (ignorado)",
                    delivery.sessionId(), delivery.vendorData());
            return WebhookOutcome.IGNORED;
        }

        verification.applyDecision(
                VerificationStatus.fromDidit(delivery.diditStatus()), delivery.diditStatus());
        verificationRepository.save(verification);
        if (verification.isVerified()) {
            promoverACreador(verification.getUserId());
        }
        return WebhookOutcome.PROCESSED;
    }

    /**
     * Regla de producto (decisión B): quien completa el KYC puede publicar.
     * Se promueve USER → CREATOR y <strong>nunca se revierte</strong>: si la
     * verificación caduca después, el creador conserva sus experiencias, sus
     * ventas y el acceso a cobros. Degradar el rol rompería datos ya creados.
     *
     * <p>Ojo: este webhook lo llama Didit, no el navegador del usuario. La
     * sesión abierta sigue con la authority vieja; {@code RoleRefreshFilter}
     * la refresca en el siguiente request.
     */
    private void promoverACreador(Long userId) {
        userRepository.findById(userId).ifPresentOrElse(user -> {
            if (user.getRole() == Role.CREATOR) {
                return;
            }
            user.setRole(Role.CREATOR);
            userRepository.save(user);
            log.info("Usuario {} promovido a CREATOR por verificación de identidad", user.getUsername());
        }, () -> log.warn("Verificación VERIFIED sin usuario con id {}", userId));
    }

    /**
     * vendor_data es el userId como String, parseado defensivo: si no es un
     * Long válido o no hay fila, se busca por session_id (fallback).
     */
    private DiditVerification buscar(WebhookDelivery delivery) {
        Long userId = parseUserId(delivery.vendorData());
        if (userId != null) {
            var porUsuario = verificationRepository.findByUserId(userId);
            if (porUsuario.isPresent()) {
                return porUsuario.get();
            }
        }
        if (delivery.sessionId() != null && !delivery.sessionId().isBlank()) {
            var porSesion = verificationRepository.findByDiditSessionId(delivery.sessionId());
            if (porSesion.isPresent()) {
                return porSesion.get();
            }
        }
        return null;
    }

    private Long parseUserId(String vendorData) {
        if (vendorData == null || vendorData.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(vendorData.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
