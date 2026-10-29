package com.openlab.mx.movo.identity.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuración de Didit. Todas las keys entran SOLO por variables de
 * entorno (nunca commiteadas); sin api-key el cliente queda no-configurado.
 */
@Component
@Getter
@Setter
public class DiditProperties {

    private static final Logger log = LoggerFactory.getLogger(DiditProperties.class);

    @Value("${didit.api-key}")
    private String apiKey;

    @Value("${didit.workflow-id}")
    private String workflowId;

    @Value("${didit.webhook-secret}")
    private String webhookSecret;

    @Value("${didit.base-url}")
    private String baseUrl;

    @Value("${didit.callback-url}")
    private String callbackUrl;

    /**
     * Log DESPUÉS de la inyección de @Value: en el constructor los campos
     * todavía están en su inicializador (vacíos), por eso un log ahí siempre
     * mostraba workflowId y callbackUrl vacíos.
     */
    @PostConstruct
    void logResuelto() {
        log.info("DiditProperties resuelto - apiKey: {}, workflowId: '{}', baseUrl: '{}', callbackUrl: '{}'",
                (apiKey == null || apiKey.isBlank()) ? "<VACÍA>" : "<seteada>",
                workflowId, baseUrl, callbackUrl);
    }
}
