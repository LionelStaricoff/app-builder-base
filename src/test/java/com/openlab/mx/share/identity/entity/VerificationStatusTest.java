package com.openlab.mx.share.identity.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationStatusTest {

    @Test
    void approved_es_verified() {
        assertEquals(VerificationStatus.VERIFIED, VerificationStatus.fromDidit("Approved"));
    }

    @Test
    void declined_es_rejected() {
        assertEquals(VerificationStatus.REJECTED, VerificationStatus.fromDidit("Declined"));
    }

    @Test
    void in_review_es_pending_manual() {
        assertEquals(VerificationStatus.PENDING_MANUAL, VerificationStatus.fromDidit("In Review"));
    }

    @Test
    void abandonado_expirado_y_kyc_expired_es_expired() {
        assertEquals(VerificationStatus.EXPIRED, VerificationStatus.fromDidit("Abandoned"));
        assertEquals(VerificationStatus.EXPIRED, VerificationStatus.fromDidit("Expired"));
        assertEquals(VerificationStatus.EXPIRED, VerificationStatus.fromDidit("Kyc Expired"));
    }

    @Test
    void literales_en_progreso_mapean_a_in_progress() {
        assertEquals(VerificationStatus.IN_PROGRESS, VerificationStatus.fromDidit("Not Started"));
        assertEquals(VerificationStatus.IN_PROGRESS, VerificationStatus.fromDidit("In Progress"));
        assertEquals(VerificationStatus.IN_PROGRESS, VerificationStatus.fromDidit("Awaiting User"));
        assertEquals(VerificationStatus.IN_PROGRESS, VerificationStatus.fromDidit("Resubmitted"));
    }

    @Test
    void literal_desconocido_lanza_excepcion() {
        assertThrows(IllegalArgumentException.class, () -> VerificationStatus.fromDidit("Future Literal"));
    }

    @Test
    void isKnown_distingue_literales_reconocidos() {
        assertTrue(VerificationStatus.isKnown("Approved"));
        assertFalse(VerificationStatus.isKnown("Future Literal"));
        assertFalse(VerificationStatus.isKnown(null));
    }
}
