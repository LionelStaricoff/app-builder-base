package com.openlab.mx.movo.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Pedido de checkout calculado SIEMPRE en el dominio (spec §11.1):
 * unit amount × quantity = total; el front nunca manda el monto.
 */
public record GatewayCheckoutRequest(
        BigDecimal unitAmount,
        String currency,
        int quantity,
        String description,
        Map<String, String> metadata) {
}
