package com.openlab.mx.share.identity.config;

import com.openlab.mx.share.identity.client.DiditRestSessionsClient;
import com.openlab.mx.share.identity.client.DiditSessionsClient;
import com.openlab.mx.share.identity.client.DiditWebhookSigner;
import com.openlab.mx.share.identity.client.UnconfiguredDiditSessionsClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wiring de Didit, mismo patrón que {@code PaymentGatewayConfig}: sin
 * api-key configurada se usa el cliente no-configurado (error claro),
 * nunca una llamada a la red.
 */
@Configuration
public class DiditConfig {

    @Bean
    DiditSessionsClient diditSessionsClient(
            ObjectProvider<RestClient.Builder> builderProvider,
            @Value("${didit.api-key:}") String apiKey,
            @Value("${didit.base-url:https://verification.didit.me}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            return new UnconfiguredDiditSessionsClient();
        }
        RestClient.Builder builder = builderProvider.getIfAvailable(RestClient::builder);
        return new DiditRestSessionsClient(builder, baseUrl, apiKey);
    }

    @Bean
    DiditWebhookSigner diditWebhookSigner(@Value("${didit.webhook-secret:}") String webhookSecret) {
        return new DiditWebhookSigner(webhookSecret);
    }
}
