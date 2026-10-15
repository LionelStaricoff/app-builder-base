package com.openlab.mx.share.shared;

import com.openlab.mx.share.identity.client.DiditSession;
import com.openlab.mx.share.identity.client.DiditSessionsClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reemplaza al DiditSessionsClient real (en tests el api-key está vacía, así
 * que el bean de producción sería el no-configurado). Nunca se llama a la red.
 */
@TestConfiguration
public class TestDiditConfig {

    @Bean
    @Primary
    DiditSessionsClient fakeDiditSessionsClient() {
        return new DiditSessionsClient() {
            @Override
            public DiditSession createSession(long userId, String workflowId, String callbackUrl) {
                return new DiditSession("fake-" + userId, "https://didit.me/fake/" + userId);
            }
        };
    }
}
