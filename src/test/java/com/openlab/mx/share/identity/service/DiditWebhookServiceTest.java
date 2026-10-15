package com.openlab.mx.share.identity.service;

import com.openlab.mx.share.identity.dto.WebhookDelivery;
import com.openlab.mx.share.identity.entity.DiditVerification;
import com.openlab.mx.share.identity.entity.VerificationStatus;
import com.openlab.mx.share.identity.repository.DiditVerificationRepository;
import com.openlab.mx.share.identity.repository.DiditWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiditWebhookServiceTest {

    @Mock
    private DiditVerificationRepository verificationRepository;
    @Mock
    private DiditWebhookEventRepository eventRepository;

    @InjectMocks
    private DiditWebhookService service;

    private WebhookDelivery delivery(String eventId, String sessionId, String vendorData, String status) {
        return new WebhookDelivery(eventId, "status.updated", sessionId, vendorData, status);
    }

    @Test
    void sin_event_id_lanza_excepcion() {
        WebhookDelivery sinId = delivery(null, "s1", "7", "Approved");

        assertThrows(InvalidWebhookPayloadException.class, () -> service.handle(sinId));

        verify(eventRepository, never()).save(any());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void evento_duplicado_es_no_op() {
        when(eventRepository.existsById("e1")).thenReturn(true);
        DiditVerification row = DiditVerification.started(7L, "s1");

        WebhookOutcome outcome = service.handle(delivery("e1", "s1", "7", "Approved"));

        assertEquals(WebhookOutcome.DUPLICATE, outcome);
        assertNull(row.getVerifiedAt());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void approved_marca_verified_con_timestamps() {
        DiditVerification row = DiditVerification.started(7L, "s1");
        when(verificationRepository.findByUserId(7L)).thenReturn(Optional.of(row));

        WebhookOutcome outcome = service.handle(delivery("e1", "s1", "7", "Approved"));

        assertEquals(WebhookOutcome.PROCESSED, outcome);
        assertEquals(VerificationStatus.VERIFIED, row.getStatus());
        assertEquals("Approved", row.getDiditStatus());
        assertNotNull(row.getVerifiedAt());
        verify(eventRepository).save(any());
    }

    @Test
    void declined_marca_rejected_sin_verified_at() {
        DiditVerification row = DiditVerification.started(7L, "s1");
        when(verificationRepository.findByUserId(7L)).thenReturn(Optional.of(row));

        service.handle(delivery("e1", "s1", "7", "Declined"));

        assertEquals(VerificationStatus.REJECTED, row.getStatus());
        assertNull(row.getVerifiedAt());
    }

    @Test
    void in_review_es_pending_manual() {
        DiditVerification row = DiditVerification.started(7L, "s1");
        when(verificationRepository.findByUserId(7L)).thenReturn(Optional.of(row));

        service.handle(delivery("e1", "s1", "7", "In Review"));

        assertEquals(VerificationStatus.PENDING_MANUAL, row.getStatus());
    }

    @Test
    void vendor_data_invalido_hace_fallback_por_session_id() {
        DiditVerification row = DiditVerification.started(7L, "s1");
        when(verificationRepository.findByDiditSessionId("s1")).thenReturn(Optional.of(row));

        WebhookOutcome outcome = service.handle(delivery("e1", "s1", "no-numerico", "Approved"));

        assertEquals(WebhookOutcome.PROCESSED, outcome);
        assertEquals(VerificationStatus.VERIFIED, row.getStatus());
        verify(verificationRepository, never()).findByUserId(any());
    }

    @Test
    void vendor_data_valido_sin_fila_hace_fallback_por_session_id() {
        DiditVerification row = DiditVerification.started(7L, "s1");
        when(verificationRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(verificationRepository.findByDiditSessionId("s1")).thenReturn(Optional.of(row));

        WebhookOutcome outcome = service.handle(delivery("e1", "s1", "7", "Approved"));

        assertEquals(WebhookOutcome.PROCESSED, outcome);
        assertEquals(VerificationStatus.VERIFIED, row.getStatus());
    }

    @Test
    void literal_futuro_se_ignora_pero_registra_el_evento() {
        DiditVerification row = DiditVerification.started(7L, "s1");

        WebhookOutcome outcome = service.handle(delivery("e1", "s1", "7", "Shiny New Status"));

        assertEquals(WebhookOutcome.IGNORED, outcome);
        assertEquals(VerificationStatus.IN_PROGRESS, row.getStatus());
        verify(eventRepository).save(any());
    }

    @Test
    void sin_fila_en_absoluto_se_ignora_registrando_el_evento() {
        when(verificationRepository.findByUserId(9L)).thenReturn(Optional.empty());
        when(verificationRepository.findByDiditSessionId("sx")).thenReturn(Optional.empty());

        WebhookOutcome outcome = service.handle(delivery("e1", "sx", "9", "Approved"));

        assertEquals(WebhookOutcome.IGNORED, outcome);
        verify(eventRepository).save(any());
    }

    @Test
    void session_id_ausente_y_vendor_data_invalido_se_ignora() {
        WebhookOutcome outcome = service.handle(delivery("e1", null, "???", "Approved"));

        assertEquals(WebhookOutcome.IGNORED, outcome);
        verify(eventRepository).save(any());
        verify(verificationRepository, never()).save(any());
    }
}
