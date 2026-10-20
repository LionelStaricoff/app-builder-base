package com.openlab.mx.share.reservation.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundPolicyTest {

    private final RefundPolicy policy = new RefundPolicy("24:0.90,0:0.50");

    @Test
    void conMasDe24hDevuelveEl90() {
        assertEquals(0, policy.rateFor(LocalDateTime.now().plusHours(48))
                .compareTo(new BigDecimal("0.90")));
        assertEquals(90, policy.percentFor(LocalDateTime.now().plusHours(48)));
    }

    @Test
    void con24hOMenosDevuelveEl50() {
        assertEquals(0, policy.rateFor(LocalDateTime.now().plusHours(10))
                .compareTo(new BigDecimal("0.50")));
        assertEquals(50, policy.percentFor(LocalDateTime.now().plusHours(10)));
    }

    @Test
    void elTramoDe24hCortaPasadoLas24hDeAnticipacion() {
        assertEquals(90, policy.percentFor(LocalDateTime.now().plusHours(25)));
        assertEquals(50, policy.percentFor(LocalDateTime.now().plusHours(23)));
    }

    @Test
    void unaFechaYaIniciadaCaeEnElUltimoTramo() {
        assertEquals(50, policy.percentFor(LocalDateTime.now().minusHours(2)));
    }

    @Test
    void elCartelDescribeCadaTramo() {
        var lineas = policy.lines();
        assertEquals(2, lineas.size());
        assertTrue(lineas.get(0).contains("90%"), lineas.get(0));
        assertTrue(lineas.get(0).contains("24 h"), lineas.get(0));
        assertTrue(lineas.get(1).contains("50%"), lineas.get(1));
    }

    @Test
    void admiteVariosTramos() {
        RefundPolicy escalera = new RefundPolicy("48:0.95,24:0.90,0:0.50");
        assertEquals(95, escalera.percentFor(LocalDateTime.now().plusHours(72)));
        assertEquals(90, escalera.percentFor(LocalDateTime.now().plusHours(30)));
        assertEquals(50, escalera.percentFor(LocalDateTime.now().plusHours(10)));
        assertEquals(3, escalera.lines().size());
    }

    @Test
    void elOrdenDeConfiguracionNoImporta() {
        RefundPolicy escalera = new RefundPolicy("0:0.50,24:0.90");
        assertEquals(90, escalera.percentFor(LocalDateTime.now().plusHours(48)));
        assertEquals(50, escalera.percentFor(LocalDateTime.now().plusHours(5)));
    }

    @Test
    void unaConfigInvalidaFallaAlArrancar() {
        assertThrows(IllegalStateException.class, () -> new RefundPolicy(""));
        assertThrows(IllegalStateException.class, () -> new RefundPolicy("basura"));
        assertThrows(IllegalStateException.class, () -> new RefundPolicy("24:abc"));
        assertThrows(IllegalStateException.class, () -> new RefundPolicy("24:1.5"));
    }
}
