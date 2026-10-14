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

    @Override
    public GatewaySession createCheckout(GatewayCheckoutRequest request) {
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

    /** Helper de test: operación registrada ("capture|cancel|capturePartial:<monto>") o null. */
    public String operationOf(String paymentIntentId) {
        return operations.get(paymentIntentId);
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
