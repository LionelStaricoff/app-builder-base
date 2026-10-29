package com.openlab.mx.movo.identity.service;

import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.entity.VerificationStatus;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.shared.exception.UnverifiedIdentityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequireVerifiedIdentityServiceTest {

    @Mock
    private DiditVerificationRepository repository;

    @InjectMocks
    private RequireVerifiedIdentityService service;

    @Test
    void sin_fila_bloquea() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());

        assertThrows(UnverifiedIdentityException.class, () -> service.requireVerified(7L));
    }

    @Test
    void in_progress_bloquea() {
        when(repository.findByUserId(7L))
                .thenReturn(Optional.of(DiditVerification.started(7L, "s1")));

        assertThrows(UnverifiedIdentityException.class, () -> service.requireVerified(7L));
    }

    @Test
    void rejected_bloquea() {
        DiditVerification rechazada = DiditVerification.started(7L, "s1");
        rechazada.applyDecision(VerificationStatus.REJECTED, "Declined");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(rechazada));

        assertThrows(UnverifiedIdentityException.class, () -> service.requireVerified(7L));
    }

    @Test
    void pending_manual_bloquea() {
        DiditVerification enRevision = DiditVerification.started(7L, "s1");
        enRevision.applyDecision(VerificationStatus.PENDING_MANUAL, "In Review");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(enRevision));

        assertThrows(UnverifiedIdentityException.class, () -> service.requireVerified(7L));
    }

    @Test
    void verified_pasa() {
        DiditVerification verificada = DiditVerification.started(7L, "s1");
        verificada.applyDecision(VerificationStatus.VERIFIED, "Approved");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(verificada));

        assertDoesNotThrow(() -> service.requireVerified(7L));
    }

    @Test
    void userId_nulo_bloquea_sin_consultar() {
        assertThrows(UnverifiedIdentityException.class, () -> service.requireVerified(null));
    }
}
