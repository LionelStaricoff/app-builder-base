package com.openlab.mx.movo.identity.client;

import com.google.gson.Gson;
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

    private static final Gson GSON = new Gson();

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

        // El body va serializado a JSON String (longitud conocida => Content-Length):
        // mandar el Map directo hace que el request salga con Transfer-Encoding:
        // chunked (sin longitud), y en ese framing Didit recibe el body vacío y
        // responde 400 {"workflow_id":["This field is required."]} de forma
        // intermitente (verificado contra su API: mismos bytes con Content-Length => 201).
        String requestJson = GSON.toJson(body);

        String raw;
        try {
            raw = restClient.post()
                    .uri("/v3/session/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .toEntity(String.class)
                    .getBody();
        } catch (RestClientResponseException e) {
            log.error("Didit respondió con error HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            throw mapStatus(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            log.error("No se pudo contactar a Didit: {}", e.getMessage(), e);
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

    /**
     * Traduce el status HTTP de Didit a un error de dominio.
     *
     * <p>La causa original se conserva: sin ella se pierde el stack trace y
     * el body de la respuesta, que es justo lo que hace falta para entender
     * por qué Didit rechazó la petición.
     */
    private DiditApiException mapStatus(int status, RestClientResponseException cause) {
        return switch (status) {
            case 403 -> new DiditApiException(
                    "Didit rechazó la petición (403): API key inválida o sin créditos", cause);
            case 429 -> new DiditApiException(
                    "Didit limitó las peticiones (429), reintentá más tarde", cause);
            default -> new DiditApiException("Didit respondió con error HTTP " + status, cause);
        };
    }
}
