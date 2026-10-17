package com.openlab.mx.share.identity.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementación real del puerto de sesiones contra
 * {@code POST {base-url}/v3/session/} con header {@code x-api-key}.
 *
 * <p>{@code vendor_data} lleva el userId como String: es el puente por el
 * que el webhook nos devuelve quién se está verificando.</p>
 */
public class DiditRestSessionsClient implements DiditSessionsClient {

    private static final Logger log = LoggerFactory.getLogger(DiditRestSessionsClient.class);

    private final RestClient restClient;

    public DiditRestSessionsClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    @Override
    public DiditSession createSession(long userId, String workflowId, String callbackUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow_id", workflowId);
        body.put("vendor_data", String.valueOf(userId));
        body.put("callback", callbackUrl);

        log.info("Creando sesión Didit - body: {}", body);
        log.info("Creando sesión Didit - workflow_id: {}, vendor_data: {}, callback: {}", workflowId, userId, callbackUrl);

        String raw;
        try {
            raw = restClient.post()
                    .uri("/v3/session/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class)
                    .getBody();
        } catch (RestClientResponseException e) {
            log.error("Didit respondió con error HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw mapStatus(e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new DiditApiException("No se pudo contactar a Didit", e);
        }

        try {
            JsonObject json = JsonParser.parseString(raw == null ? "" : raw).getAsJsonObject();
            String sessionId = json.get("session_id").getAsString();
            String url = json.get("url").getAsString();
            return new DiditSession(sessionId, url);
        } catch (RuntimeException e) {
            throw new DiditApiException("Respuesta ilegible de Didit al crear la sesión", e);
        }
    }

    private DiditApiException mapStatus(int status) {
        return switch (status) {
            case 403 -> new DiditApiException(
                    "Didit rechazó la petición (403): API key inválida o sin créditos");
            case 429 -> new DiditApiException(
                    "Didit limitó las peticiones (429), reintentá más tarde");
            default -> new DiditApiException("Didit respondió con error HTTP " + status);
        };
    }
}
