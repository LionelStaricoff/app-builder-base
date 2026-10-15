package com.openlab.mx.share.shared;

import com.openlab.mx.share.identity.service.RequireVerifiedIdentityService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * El gate de KYC no debe romper los tests de otros features (checkout,
 * cancelación, chat, multipart...): este bean @Primary deja pasar a todos.
 *
 * OJO: solo lo importan los tests de otros features. El gate REAL se testea
 * en {@code IdentityGateFlowTests} y en
 * {@code RequireVerifiedIdentityServiceTest}, que NO importan esta config.
 */
@TestConfiguration
public class TestIdentityGateConfig {

    @Bean
    @Primary
    RequireVerifiedIdentityService identityGateQueDejaPasar() {
        return new RequireVerifiedIdentityService(null) {
            @Override
            public void requireVerified(Long userId) {
                // no-op a propósito en tests de otros features
            }
        };
    }
}
