package com.openlab.mx.share.payment.gateway;

/**
 * Resultado de crear/consultar una Checkout Session (spec §4).
 * status: open | paid | unpaid | expired (formato con el que la pasarela de hecho responde).
 */
public record GatewaySession(String sessionId, String url, String paymentIntentId, String status) {
}
