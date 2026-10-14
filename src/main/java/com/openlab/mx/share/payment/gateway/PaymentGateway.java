package com.openlab.mx.share.payment.gateway;

import java.math.BigDecimal;

/**
 * Puerto hacia la pasarela de pago (spec §5): única vía por la que el dominio
 * hablando con Stripe. La implementación @Primary es el adapter real;
 * los tests usan FakePaymentGateway.
 */
public interface PaymentGateway {

    /** Crea la Checkout Session y devuelve su URL de redirect. */
    GatewaySession createCheckout(GatewayCheckoutRequest request);

    /**
     * Revalida el estado real de una sesión contra la pasarela. Un
     * session_id desconocido falla; nunca se confía en query params (spec §6).
     */
    GatewaySession authorizeInfo(String sessionId);

    /** Captura la plena del PaymentIntent autorizado (release de escrow). */
    void capture(String paymentIntentId);

    /** Captura parcial (refund del 50%: la penalidad queda en la plataforma). */
    void capturePartial(String paymentIntentId, BigDecimal amount);

    /** Cancela la autorización sin capturar (refund del 100%). */
    void cancel(String paymentIntentId);
}
