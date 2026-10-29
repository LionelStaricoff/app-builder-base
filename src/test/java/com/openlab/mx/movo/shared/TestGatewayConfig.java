package com.openlab.mx.movo.shared;

import com.openlab.mx.movo.payment.gateway.FakePaymentGateway;
import com.openlab.mx.movo.payment.gateway.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Impide que MVC tests llamen a Stripe real: el fake pasa como
 * PaymentGateway @Primary sobre el bean de PaymentGatewayConfig.
 */
@TestConfiguration
public class TestGatewayConfig {

    @Bean
    @Primary
    PaymentGateway fakePaymentGateway() {
        return new FakePaymentGateway();
    }
}
