package com.openlab.mx.share.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único punto de armado del puerto (spec §8): con secret-key → adapter Stripe;
 * sin secret-key → UnconfiguredPaymentGateway (mensaje claro al pagar).
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        if (secretKey == null || secretKey.isBlank()) {
            return new UnconfiguredPaymentGateway();
        }
        return new StripePaymentGateway(secretKey, baseUrl);
    }
}
