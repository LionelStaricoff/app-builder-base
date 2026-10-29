package com.openlab.mx.movo.payment.gateway;

import com.openlab.mx.movo.shared.exception.PaymentUnavailableException;

import java.math.BigDecimal;

/**
 * Fallback del puerto cuando no hay keys de Stripe (spec §8): cualquier uso
 * lanza un mensaje claro — nunca NullPointerException ni silencio.
 */
public class UnconfiguredPaymentGateway implements PaymentGateway {

    private PaymentUnavailableException noDisponible() {
        return new PaymentUnavailableException();
    }

    @Override
    public GatewaySession createCheckout(GatewayCheckoutRequest request) {
        throw noDisponible();
    }

    @Override
    public GatewaySession authorizeInfo(String sessionId) {
        throw noDisponible();
    }

    @Override
    public void capture(String paymentIntentId) {
        throw noDisponible();
    }

    @Override
    public void capturePartial(String paymentIntentId, BigDecimal amount) {
        throw noDisponible();
    }

    @Override
    public void cancel(String paymentIntentId) {
        throw noDisponible();
    }

    // ─── Stripe Connect ───────────────────────────────────────────────────

    @Override
    public String createConnectAccount(String country, String email) {
        throw noDisponible();
    }

    @Override
    public String createAccountLink(String stripeAccountId, String refreshUrl, String returnUrl) {
        throw noDisponible();
    }

    @Override
    public ConnectAccountInfo retrieveConnectAccount(String stripeAccountId) {
        throw noDisponible();
    }

    @Override
    public String transferToConnectedAccount(String stripeAccountId, BigDecimal amount, String currency, String idempotencyKey) {
        throw noDisponible();
    }
}
