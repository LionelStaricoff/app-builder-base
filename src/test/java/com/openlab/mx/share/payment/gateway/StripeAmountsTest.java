package com.openlab.mx.share.payment.gateway;

import com.openlab.mx.share.shared.exception.PaymentUnavailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Spec §8: montos en centavos (× 100) y fallback sin keys con mensaje claro.
 */
class StripeAmountsTest {

    @Test
    void toCentsMultiplicaPorCien() {
        assertEquals(38000L, StripePaymentGateway.toCents(new BigDecimal("380.00")));
        assertEquals(1905L, StripePaymentGateway.toCents(new BigDecimal("19.05")));
        assertEquals(99L, StripePaymentGateway.toCents(new BigDecimal("0.99")));
        assertEquals(1L, StripePaymentGateway.toCents(new BigDecimal("0.01")));
    }

    @Test
    void toCentsDeNullFallaConMensajeClaro() {
        assertThrows(IllegalArgumentException.class,
                () -> StripePaymentGateway.toCents(null));
    }

    @Test
    void unconfiguredLanzaPaymentUnavailableEnTodoElPuerto() {
        UnconfiguredPaymentGateway g = new UnconfiguredPaymentGateway();
        GatewayCheckoutRequest r = new GatewayCheckoutRequest(
                BigDecimal.ONE, "MXN", 1, "Exp", java.util.Map.of());

        assertThrows(PaymentUnavailableException.class, () -> g.createCheckout(r));
        assertThrows(PaymentUnavailableException.class, () -> g.authorizeInfo("cs_x"));
        assertThrows(PaymentUnavailableException.class, () -> g.capture("pi_x"));
        assertThrows(PaymentUnavailableException.class, () -> g.capturePartial("pi_x", BigDecimal.ONE));
        assertThrows(PaymentUnavailableException.class, () -> g.cancel("pi_x"));
    }
}
