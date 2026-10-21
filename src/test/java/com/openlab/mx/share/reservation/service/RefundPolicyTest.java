package com.openlab.mx.share.reservation.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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

    @Test
    void conUnRelojFijoElTramoEsDeterminista() {
        // 2026-10-20T12:00Z == 2026-10-20 06:00 en Ciudad de México (UTC-6).
        Clock fijo = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneId.of("America/Mexico_City"));
        RefundPolicy congelada = new RefundPolicy("24:0.90,0:0.50", fijo);

        // 30 h de anticipación → 90 %.
        assertEquals(90, congelada.percentFor(LocalDateTime.of(2026, 10, 21, 12, 0)));
        // 10 h de anticipación → 50 %.
        assertEquals(50, congelada.percentFor(LocalDateTime.of(2026, 10, 20, 16, 0)));
        // Fecha ya iniciada → 50 %.
        assertEquals(50, congelada.percentFor(LocalDateTime.of(2026, 10, 20, 4, 0)));
    }
}
