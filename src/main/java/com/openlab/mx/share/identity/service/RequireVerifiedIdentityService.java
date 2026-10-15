package com.openlab.mx.share.identity.service;

import com.openlab.mx.share.identity.repository.DiditVerificationRepository;
import com.openlab.mx.share.shared.exception.UnverifiedIdentityException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gate de identidad verificada: lo usan los otros features desde sus
 * servicios (reservar, publicar). Solo {@code VERIFIED} pasa; cualquier
 * otro estado (incluido no tener fila) lanza
 * {@link UnverifiedIdentityException}, que el GlobalExceptionHandler
 * convierte en redirect a /verificacion.
 */
@Service
@RequiredArgsConstructor
public class RequireVerifiedIdentityService {

    private final DiditVerificationRepository repository;

    @Transactional(readOnly = true)
    public void requireVerified(Long userId) {
        boolean verified = userId != null && repository.findByUserId(userId)
                .map(v -> v.isVerified())
                .orElse(false);
        if (!verified) {
            throw new UnverifiedIdentityException();
        }
    }
}
