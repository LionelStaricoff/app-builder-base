package com.openlab.mx.movo.identity.service;

import com.openlab.mx.movo.identity.client.DiditApiException;
import com.openlab.mx.movo.identity.client.DiditSession;
import com.openlab.mx.movo.identity.client.DiditSessionsClient;
import com.openlab.mx.movo.identity.config.DiditProperties;
import com.openlab.mx.movo.identity.dto.VerificationStatusResponse;
import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.entity.VerificationStatus;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.shared.exception.VerificationAlreadyCompletedException;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationSessionServiceTest {

    @Mock
    private DiditVerificationRepository repository;
    @Mock
    private DiditSessionsClient sessionsClient;
    @Mock
    private DiditProperties properties;

    @InjectMocks
    private VerificationSessionService service;

    @BeforeEach
    void configurarProperties() {
        org.mockito.Mockito.lenient().when(properties.getWorkflowId()).thenReturn("wf-1");
        org.mockito.Mockito.lenient()
                .when(properties.getCallbackUrl()).thenReturn("https://app/verificacion/callback");
    }

    private User user(long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    @Test
    void sin_fila_previa_crea_verificacion_en_progreso() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(sessionsClient.createSession(7L, "wf-1", "https://app/verificacion/callback"))
                .thenReturn(new DiditSession("s1", "https://didit.me/s1"));

        DiditSession result = service.start(user(7L));

        assertEquals(new DiditSession("s1", "https://didit.me/s1"), result);
        ArgumentCaptor<DiditVerification> captor = ArgumentCaptor.forClass(DiditVerification.class);
        verify(repository).save(captor.capture());
        DiditVerification guardada = captor.getValue();
        assertEquals(7L, guardada.getUserId());
        assertEquals("s1", guardada.getDiditSessionId());
        assertEquals(VerificationStatus.IN_PROGRESS, guardada.getStatus());
    }

    @Test
    void rechazada_reintenta_con_sesion_nueva_y_decisiones_limpias() {
        DiditVerification rechazada = DiditVerification.started(7L, "s1");
        rechazada.applyDecision(VerificationStatus.REJECTED, "Declined");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(rechazada));
        when(sessionsClient.createSession(anyLong(), anyString(), anyString()))
                .thenReturn(new DiditSession("s2", "https://didit.me/s2"));

        service.start(user(7L));

        ArgumentCaptor<DiditVerification> captor = ArgumentCaptor.forClass(DiditVerification.class);
        verify(repository).save(captor.capture());
        DiditVerification guardada = captor.getValue();
        assertEquals(VerificationStatus.IN_PROGRESS, guardada.getStatus());
        assertEquals("s2", guardada.getDiditSessionId());
        assertNull(guardada.getVerifiedAt());
        assertNull(guardada.getDecidedAt());
    }

    @Test
    void expirada_tambien_reintenta() {
        DiditVerification expirada = DiditVerification.started(7L, "s1");
        expirada.applyDecision(VerificationStatus.EXPIRED, "Expired");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(expirada));
        when(sessionsClient.createSession(anyLong(), anyString(), anyString()))
                .thenReturn(new DiditSession("s2", "https://didit.me/s2"));

        service.start(user(7L));

        assertEquals(VerificationStatus.IN_PROGRESS, expirada.getStatus());
        assertEquals("s2", expirada.getDiditSessionId());
    }

    @Test
    void en_progreso_genera_sesion_nueva() {
        DiditVerification enCurso = DiditVerification.started(7L, "s1");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(enCurso));
        when(sessionsClient.createSession(anyLong(), anyString(), anyString()))
                .thenReturn(new DiditSession("s2", "https://didit.me/s2"));

        service.start(user(7L));

        assertEquals("s2", enCurso.getDiditSessionId());
    }

    @Test
    void verified_lanza_excepcion_y_no_llama_a_didit() {
        DiditVerification verificada = DiditVerification.started(7L, "s1");
        verificada.applyDecision(VerificationStatus.VERIFIED, "Approved");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(verificada));

        assertThrows(VerificationAlreadyCompletedException.class, () -> service.start(user(7L)));

        verify(sessionsClient, never()).createSession(anyLong(), anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void error_de_didit_propaga_sin_alterar_el_estado() {
        DiditVerification rechazada = DiditVerification.started(7L, "s1");
        rechazada.applyDecision(VerificationStatus.REJECTED, "Declined");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(rechazada));
        when(sessionsClient.createSession(anyLong(), anyString(), anyString()))
                .thenThrow(new DiditApiException("Didit respondió con error HTTP 429"));

        assertThrows(DiditApiException.class, () -> service.start(user(7L)));

        assertEquals(VerificationStatus.REJECTED, rechazada.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void getStatus_sin_fila_devuelve_not_started() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());

        VerificationStatusResponse status = service.getStatus(user(7L));

        assertEquals("NOT_STARTED", status.status());
        assertNull(status.verifiedAt());
    }

    @Test
    void getStatus_refleja_verified_con_timestamp() {
        DiditVerification verificada = DiditVerification.started(7L, "s1");
        verificada.applyDecision(VerificationStatus.VERIFIED, "Approved");
        when(repository.findByUserId(7L)).thenReturn(Optional.of(verificada));

        VerificationStatusResponse status = service.getStatus(user(7L));

        assertEquals("VERIFIED", status.status());
        assertNotNull(status.verifiedAt());
    }
}
