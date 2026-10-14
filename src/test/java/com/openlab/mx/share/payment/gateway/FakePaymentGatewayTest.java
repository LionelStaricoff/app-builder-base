package com.openlab.mx.share.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Spec §5: el fake simula todos los caminos del puerto PaymentGateway.
 */
class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();

    @Test
    void createCheckoutGeneraSesionesSecuenciales() {
        GatewayCheckoutRequest r = new GatewayCheckoutRequest(
                new BigDecimal("380.00"), "MXN", 2, "Experiencia",
                Map.of("instanceId", "1", "userId", "10"));
        GatewaySession s1 = gateway.createCheckout(r);
        GatewaySession s2 = gateway.createCheckout(r);

        assertNotNull(s1.url());
        assertNotNull(s1.paymentIntentId());
        assertEquals("cs_fake_1", s1.sessionId());
        assertEquals("cs_fake_2", s2.sessionId(), "segunda llamada genera cs_fake_2");
        assertEquals("open", s1.status());
    }

    @Test
    void authorizeInfoRetornaPaidPorDefecto() {
        GatewaySession s = gateway.createCheckout(new GatewayCheckoutRequest(
                new BigDecimal("100.00"), "MXN", 1, "Exp", Map.of()));
        assertEquals("paid", gateway.authorizeInfo(s.sessionId()).status());
    }

    @Test
    void markUnpaidCambiaElEstado() {
        GatewaySession s = gateway.createCheckout(new GatewayCheckoutRequest(
                new BigDecimal("100.00"), "MXN", 1, "Exp", Map.of()));
        gateway.markUnpaid(s.sessionId());
        assertEquals("unpaid", gateway.authorizeInfo(s.sessionId()).status());
    }

    @Test
    void authorizeInfoDeSesionDesconocidaFalla() {
        assertThrows(NoSuchElementException.class,
                () -> gateway.authorizeInfo("cs_inexistente"));
    }

    @Test
    void capturasYCancelacionesQuedanRegistradas() {
        gateway.capture("pi_fake_1");
        gateway.cancel("pi_fake_2");
        gateway.capturePartial("pi_fake_3", new BigDecimal("190.00"));

        assertEquals("capture", gateway.operationOf("pi_fake_1"));
        assertEquals("cancel", gateway.operationOf("pi_fake_2"));
        assertEquals("capturePartial:190.00", gateway.operationOf("pi_fake_3"));
        assertNull(gateway.operationOf("pi_desconocido"), "sin operación previa");
    }
}
