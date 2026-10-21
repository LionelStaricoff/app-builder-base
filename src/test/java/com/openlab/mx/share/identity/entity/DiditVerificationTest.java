package com.openlab.mx.share.identity.entity;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiditVerificationTest {

    @Test
    void started_arranca_en_in_progress() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");

        assertEquals(7L, v.getUserId());
        assertEquals("sess-1", v.getDiditSessionId());
        assertEquals(VerificationStatus.IN_PROGRESS, v.getStatus());
        assertFalse(v.isVerified());
        assertNull(v.getVerifiedAt());
        assertNull(v.getDecidedAt());
    }

    @Test
    void applyDecision_approved_marca_verified_con_timestamps() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");

        v.applyDecision(VerificationStatus.VERIFIED, "Approved");

        assertEquals(VerificationStatus.VERIFIED, v.getStatus());
        assertEquals("Approved", v.getDiditStatus());
        assertNotNull(v.getVerifiedAt());
        assertNotNull(v.getDecidedAt());
        assertTrue(v.isVerified());
    }

    @Test
    void applyDecision_rechazada_no_setea_verified_at() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");

        v.applyDecision(VerificationStatus.REJECTED, "Declined");

        assertEquals(VerificationStatus.REJECTED, v.getStatus());
        assertNull(v.getVerifiedAt());
        assertNotNull(v.getDecidedAt());
        assertFalse(v.isVerified());
    }

    @Test
    void restart_desde_rejected_crea_sesion_nueva_y_limpia_decisiones() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");
        v.applyDecision(VerificationStatus.REJECTED, "Declined");

        v.restart("sess-2");

        assertEquals(VerificationStatus.IN_PROGRESS, v.getStatus());
        assertEquals("sess-2", v.getDiditSessionId());
        assertNull(v.getVerifiedAt());
        assertNull(v.getDecidedAt());
        assertNull(v.getDiditStatus());
    }

    @Test
    void restart_desde_expired_es_valido() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");
        v.applyDecision(VerificationStatus.EXPIRED, "Expired");

        v.restart("sess-2");

        assertEquals(VerificationStatus.IN_PROGRESS, v.getStatus());
        assertEquals("sess-2", v.getDiditSessionId());
    }

    @Test
    void restart_sobre_verified_es_terminal() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");
        v.applyDecision(VerificationStatus.VERIFIED, "Approved");

        assertThrows(IllegalStateException.class, () -> v.restart("sess-2"));
        assertEquals("sess-1", v.getDiditSessionId());
    }

    @Test
    void restart_desde_in_progress_genera_sesion_nueva() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");

        v.restart("sess-2");

        assertEquals(VerificationStatus.IN_PROGRESS, v.getStatus());
        assertEquals("sess-2", v.getDiditSessionId());
    }

    @Test
    void applyDecision_no_degrada_un_verified() {
        DiditVerification v = DiditVerification.started(7L, "sess-1");
        v.applyDecision(VerificationStatus.VERIFIED, "Approved");

        v.applyDecision(VerificationStatus.REJECTED, "Declined");

        assertEquals(VerificationStatus.VERIFIED, v.getStatus());
        assertEquals("Approved", v.getDiditStatus());
        assertTrue(v.isVerified());
    }
}
