package com.openlab.mx.movo.payment.gateway;

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

    // ─── Stripe Connect ───────────────────────────────────────────────────

    /** Crea una cuenta Connect (Express) para un creador. Devuelve el acct_id. */
    String createConnectAccount(String country, String email);

    /** Genera un Account Link temporal para Hosted Onboarding. */
    String createAccountLink(String stripeAccountId, String refreshUrl, String returnUrl);

    /** Consulta el estado real de una cuenta Connect desde Stripe. */
    ConnectAccountInfo retrieveConnectAccount(String stripeAccountId);

    /** Ejecuta un transfer de la plataforma a una cuenta conectada. */
    String transferToConnectedAccount(String stripeAccountId, BigDecimal amount, String currency, String idempotencyKey);

    /** Información relevante de una cuenta Connect para decidir si puede recibir pagos. */
    record ConnectAccountInfo(
            String id,
            boolean chargesEnabled,
            boolean payoutsEnabled,
            String disabledReason
    ) {}
}
