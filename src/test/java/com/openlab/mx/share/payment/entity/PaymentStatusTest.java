package com.openlab.mx.share.payment.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Spec §3: estados PENDING/FAILED y columnas de referencia Stripe en Payment.
 */
class PaymentStatusTest {

    @Test
    void enumTieneLosSeisEstados() {
        Set<PaymentStatus> estados = EnumSet.allOf(PaymentStatus.class);
        assertEquals(Set.of(PaymentStatus.HELD, PaymentStatus.RELEASED,
                        PaymentStatus.REFUNDED_FULL, PaymentStatus.REFUNDED_PARTIAL,
                        PaymentStatus.PENDING, PaymentStatus.FAILED),
                estados, "deben existir exactamente los 6 estados de spec §3.2");
    }

    @Test
    void paymentAceptaReferenciasStripe() {
        Payment p = Payment.builder()
                .stripeSessionId("cs_test_x")
                .stripePaymentIntentId("pi_test_y")
                .build();
        assertEquals("cs_test_x", p.getStripeSessionId());
        assertEquals("pi_test_y", p.getStripePaymentIntentId());
    }

    @Test
    void paymentSinReferenciasStripeQuedaNull() {
        Payment p = Payment.builder().build();
        assertNull(p.getStripeSessionId());
        assertNull(p.getStripePaymentIntentId());
    }
}
