package com.openlab.mx.share.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Doble de test del puerto (spec §10): estado in-memory. Sin bean de
 * producción — se inyecta directo en unit tests o vía @TestConfiguration.
 * authorizeInfo devuelve "paid" por defecto; markUnpaid cambia esa respuesta.
 */
public class FakePaymentGateway implements PaymentGateway {

    private record FakeSession(String paymentIntentId, String status) {
    }

    private final Map<String, FakeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> operations = new ConcurrentHashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();
    private GatewayCheckoutRequest ultimoRequest;

    @Override
    public GatewaySession createCheckout(GatewayCheckoutRequest request) {
        this.ultimoRequest = request;
        int n = secuencia.incrementAndGet();
        String sessionId = "cs_fake_" + n;
        String paymentIntentId = "pi_fake_" + n;
        sessions.put(sessionId, new FakeSession(paymentIntentId, "open"));
        return new GatewaySession(sessionId, "/__fake_stripe_/checkout/" + sessionId, paymentIntentId, "open");
    }

    @Override
    public GatewaySession authorizeInfo(String sessionId) {
        FakeSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NoSuchElementException("sesión de pago no encontrada: " + sessionId);
        }
        String status = session.status().equals("open") ? "paid" : session.status();
        return new GatewaySession(sessionId, "/__fake_stripe_/checkout/" + sessionId,
                session.paymentIntentId(), status);
    }

    @Override
    public void capture(String paymentIntentId) {
        operations.put(paymentIntentId, "capture");
    }

    @Override
    public void capturePartial(String paymentIntentId, BigDecimal amount) {
        operations.put(paymentIntentId, "capturePartial:" + amount.toPlainString());
    }

    @Override
    public void cancel(String paymentIntentId) {
        operations.put(paymentIntentId, "cancel");
    }

    // ─── Stripe Connect ───────────────────────────────────────────────────

    private final Map<String, ConnectAccountInfo> connectAccounts = new ConcurrentHashMap<>();

    @Override
    public String createConnectAccount(String country, String email) {
        String accountId = "acct_fake_" + secuencia.incrementAndGet();
        connectAccounts.put(accountId, new ConnectAccountInfo(accountId, false, false, null));
        return accountId;
    }

    @Override
    public String createAccountLink(String stripeAccountId, String refreshUrl, String returnUrl) {
        return "https://connect.stripe.com/fake/onboarding/" + stripeAccountId;
    }

    @Override
    public ConnectAccountInfo retrieveConnectAccount(String stripeAccountId) {
        return connectAccounts.getOrDefault(stripeAccountId,
                new ConnectAccountInfo(stripeAccountId, true, true, null));
    }

    @Override
    public String transferToConnectedAccount(String stripeAccountId, BigDecimal amount, String currency, String idempotencyKey) {
        String transferId = "tr_fake_" + secuencia.incrementAndGet();
        operations.put(transferId, "transfer:" + stripeAccountId + ":" + amount.toPlainString() + ":" + currency);
        return transferId;
    }

    /** Helper de test: simula que una cuenta Connect fue activada por onboarding. */
    public void markAccountActive(String stripeAccountId) {
        connectAccounts.put(stripeAccountId, new ConnectAccountInfo(stripeAccountId, true, true, null));
    }

    /** Helper de test: estado de una cuenta Connect. */
    public ConnectAccountInfo connectAccountInfo(String stripeAccountId) {
        return connectAccounts.get(stripeAccountId);
    }

    /** Helper de test: operación registrada ("capture|cancel|capturePartial:<monto>") o null. */
    public String operationOf(String paymentIntentId) {
        return operations.get(paymentIntentId);
    }

    /** Helper de test: último GatewayCheckoutRequest recibido (para asserts de montos/moneda/metadata). */
    public GatewayCheckoutRequest lastRequest() {
        return ultimoRequest;
    }

    /** Helper de test: la próxima authorizeInfo de esa sesión reportará "unpaid". */
    public void markUnpaid(String sessionId) {
        sessions.computeIfPresent(sessionId,
                (id, s) -> new FakeSession(s.paymentIntentId(), "unpaid"));
    }

    /** Helper de test: estado crudo de la sesión ("open|unpaid") o null. */
    public String statusOf(String sessionId) {
        FakeSession s = sessions.get(sessionId);
        return s != null ? s.status() : null;
    }
}
