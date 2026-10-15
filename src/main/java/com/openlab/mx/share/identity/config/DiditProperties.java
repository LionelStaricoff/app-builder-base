package com.openlab.mx.share.identity.config;

import lombok.Getter;
import lombok.Setter;
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

    @Value("${didit.api-key:}")
    private String apiKey = "";

    @Value("${didit.workflow-id:}")
    private String workflowId = "";

    @Value("${didit.webhook-secret:}")
    private String webhookSecret = "";

    @Value("${didit.base-url:https://verification.didit.me}")
    private String baseUrl = "https://verification.didit.me";

    @Value("${didit.callback-url:}")
    private String callbackUrl = "";
}
