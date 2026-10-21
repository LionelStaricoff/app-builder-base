package com.openlab.mx.share.identity.service;

import com.openlab.mx.share.identity.client.DiditSession;
import com.openlab.mx.share.identity.client.DiditSessionsClient;
import com.openlab.mx.share.identity.config.DiditProperties;import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openlab.mx.share.identity.dto.VerificationStatusResponse;
import com.openlab.mx.share.identity.entity.DiditVerification;
import com.openlab.mx.share.identity.repository.DiditVerificationRepository;
import com.openlab.mx.share.shared.exception.VerificationAlreadyCompletedException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Iniciar / reintentar la verificación de identidad y consultar su estado.
 * El back es source of truth: la fila del usuario arranca IN_PROGRESS y el
 * webhook de Didit es quien la mueve a VERIFIED | REJECTED | ... .
 */
@Service
@RequiredArgsConstructor
public class VerificationSessionService {

    private static final Logger log = LoggerFactory.getLogger(VerificationSessionService.class);

    private final DiditVerificationRepository repository;
    private final DiditSessionsClient sessionsClient;
    private final DiditProperties properties;

    /**
     * Crea una sesión hosteada en Didit para el usuario. Si ya tiene fila:
     * VERIFIED lanza {@link VerificationAlreadyCompletedException}; cualquier
     * otro estado genera una sesión nueva (reintento).
     */
    @Transactional
    public DiditSession start(User user) {
        log.info("Iniciando verificación Didit para usuario: {}", user.getId());
        log.info("DiditProperties - workflowId: '{}', callbackUrl: '{}'", properties.getWorkflowId(), properties.getCallbackUrl());
        DiditVerification verification = repository.findByUserId(user.getId()).orElse(null);
        if (verification != null && verification.isVerified()) {
            throw new VerificationAlreadyCompletedException();
        }

        DiditSession session = sessionsClient.createSession(
                user.getId(), properties.getWorkflowId(), properties.getCallbackUrl());

        if (verification == null) {
            repository.save(DiditVerification.started(user.getId(), session.sessionId()));
        } else {
            verification.restart(session.sessionId());
            repository.save(verification);
        }
        log.info("Sesión Didit guardada con ID: {}", session.sessionId());
        return session;
    }

    /** Estado del usuario actual; NOT_STARTED si nunca intentó verificar. */
    @Transactional(readOnly = true)
    public VerificationStatusResponse getStatus(User user) {
        return repository.findByUserId(user.getId())
                .map(v -> new VerificationStatusResponse(v.getStatus().name(), v.getVerifiedAt()))
                .orElseGet(() -> new VerificationStatusResponse("NOT_STARTED", null));
    }
}
