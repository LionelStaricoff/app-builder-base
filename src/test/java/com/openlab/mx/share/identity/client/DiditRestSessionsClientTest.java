package com.openlab.mx.share.identity.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiditRestSessionsClientTest {

    private static final String BASE = "https://verification.didit.me";
    private static final String CREATE = BASE + "/v3/session/";

    // Un builder solo, con el server mockeado atado ANTES de construir el client.
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DiditRestSessionsClient client = new DiditRestSessionsClient(builder, BASE, "key_test");

    @Test
    void createSession_postea_a_v3_session_con_headers_y_body() {
        server.expect(requestTo(CREATE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "key_test"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json(
                        "{\"workflow_id\":\"wf-1\",\"vendor_data\":\"42\","
                                + "\"callback\":\"https://app/verificacion/callback\"}"))
                .andRespond(withSuccess(
                        "{\"session_id\":\"s-1\",\"url\":\"https://didit.me/v3/session/s-1/\"}",
                        MediaType.APPLICATION_JSON));

        DiditSession session = client.createSession(42L, "wf-1", "https://app/verificacion/callback");

        assertEquals(new DiditSession("s-1", "https://didit.me/v3/session/s-1/"), session);
        server.verify();
    }

    @Test
    void error_403_se_mapea_a_expcion_de_api() {
        server.expect(requestTo(CREATE)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        DiditApiException e = assertThrows(DiditApiException.class,
                () -> client.createSession(1L, "wf", "cb"));

        assertTrue(e.getMessage().contains("403"));
    }

    @Test
    void error_429_se_mapea_a_expcion_de_api() {
        server.expect(requestTo(CREATE)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        DiditApiException e = assertThrows(DiditApiException.class,
                () -> client.createSession(1L, "wf", "cb"));

        assertTrue(e.getMessage().contains("429"));
    }

    @Test
    void error_5xx_se_mapea_a_expcion_de_api() {
        server.expect(requestTo(CREATE)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        DiditApiException e = assertThrows(DiditApiException.class,
                () -> client.createSession(1L, "wf", "cb"));

        assertTrue(e.getMessage().contains("503"));
    }

    @Test
    void respuesta_vacia_se_mapea_a_expcion_de_api() {
        server.expect(requestTo(CREATE)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThrows(DiditApiException.class, () -> client.createSession(1L, "wf", "cb"));
    }

    @Test
    void respuesta_incompleta_se_mapea_a_expcion_de_api() {
        server.expect(requestTo(CREATE))
                .andRespond(withSuccess("{\"session_id\":\"s-1\"}", MediaType.APPLICATION_JSON));

        assertThrows(DiditApiException.class, () -> client.createSession(1L, "wf", "cb"));
    }

    @Test
    void sin_api_key_el_cliente_no_configurado_falla_con_mensaje_claro() {
        DiditSessionsClient sinConfigurar = new UnconfiguredDiditSessionsClient();

        DiditApiException e = assertThrows(DiditApiException.class,
                () -> sinConfigurar.createSession(1L, "wf", "cb"));

        assertTrue(e.getMessage().toLowerCase().contains("configur"));
    }
}
