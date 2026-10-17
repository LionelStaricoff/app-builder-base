package com.openlab.mx.share.identity.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openlab.mx.share.identity.client.DiditSession;
import com.openlab.mx.share.identity.dto.VerificationStatusResponse;
import com.openlab.mx.share.identity.service.VerificationSessionService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.shared.exception.VerificationAlreadyCompletedException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de verificación de identidad (auth siempre: el usuario sale del
 * Principal, NUNCA del body). El front redirige con window.location.href a
 * la {@code url} devuelta (flujo hosteado de Didit, full page). Si ya está
 * VERIFIED responde 409 con código claro.
 */
@RestController
@RequiredArgsConstructor
public class VerificationController {

    private static final Logger log = (Logger) LoggerFactory.getLogger(VerificationController.class);

    public static final String CODE_ALREADY_VERIFIED = "IDENTITY_ALREADY_VERIFIED";

    private final VerificationSessionService sessionService;
    private final CurrentUser currentUser;

    @PostMapping("/api/didit/sessions")
    public ResponseEntity<?> createSession() {
        User user = currentUser.require();
        try {
            log.info("Iniciando sesión de verificación Didit para usuario: {}", user.getId());
            DiditSession session = sessionService.start(user);
            return ResponseEntity.ok(session);
        } catch (VerificationAlreadyCompletedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError(CODE_ALREADY_VERIFIED, e.getMessage()));
        }
    }

    @GetMapping("/api/didit/status")
    public VerificationStatusResponse status() {
        log.info("Consultando estado de verificación Didit para usuario");
        return sessionService.getStatus(currentUser.require());
    }

    public record ApiError(String code, String message) {
    }
}
