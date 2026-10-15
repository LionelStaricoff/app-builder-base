package com.openlab.mx.share.identity.client;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiditWebhookSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final DiditWebhookSigner signer = new DiditWebhookSigner(
            "whsec_test", Clock.fixed(NOW, ZoneOffset.UTC));

    private String timestamp() {
        return String.valueOf(NOW.getEpochSecond());
    }

    @Test
    void firma_valida_es_aceptada() {
        String body = "{\"event_id\":\"e1\",\"status\":\"Approved\"}";
        String signature = signer.sign(signer.canonicalize(body));

        assertTrue(signer.verify(body, signature, timestamp()));
    }

    @Test
    void firma_invalida_es_rechazada() {
        String body = "{\"event_id\":\"e1\",\"status\":\"Approved\"}";

        assertFalse(signer.verify(body, "deadbeef", timestamp()));
    }

    @Test
    void timestamp_viejo_fuera_de_ventana_es_rechazado() {
        String body = "{\"event_id\":\"e1\"}";
        String signature = signer.sign(signer.canonicalize(body));
        String viejo = String.valueOf(NOW.getEpochSecond() - 400);

        assertFalse(signer.verify(body, signature, viejo));
    }

    @Test
    void timestamp_futuro_fuera_de_ventana_es_rechazado() {
        String body = "{\"event_id\":\"e1\"}";
        String signature = signer.sign(signer.canonicalize(body));
        String futuro = String.valueOf(NOW.getEpochSecond() + 400);

        assertFalse(signer.verify(body, signature, futuro));
    }

    @Test
    void headers_ausentes_es_rechazado() {
        String body = "{\"event_id\":\"e1\"}";

        assertFalse(signer.verify(body, null, timestamp()));
        assertFalse(signer.verify(body, "", timestamp()));
        assertFalse(signer.verify(body, signer.sign("{}"), null));
        assertFalse(signer.verify(body, signer.sign("{}"), "no-numerico"));
        assertFalse(signer.verify(null, signer.sign("{}"), timestamp()));
    }

    @Test
    void canonical_clavea_keys_ordenadas() {
        String body = "{\"b\":1,\"a\":2}";

        assertEquals("{\"a\":2,\"b\":1}", signer.canonicalize(body));
    }

    @Test
    void canonical_ordena_recursivamente() {
        String body = "{\"z\":{\"d\":1,\"c\":2},\"a\":[{\"y\":1,\"x\":2}]}";

        assertEquals("{\"a\":[{\"x\":2,\"y\":1}],\"z\":{\"c\":2,\"d\":1}}",
                signer.canonicalize(body));
    }

    @Test
    void canonical_convierte_floats_enteros_a_long() {
        String body = "{\"score\":126.0,\"ratio\":1.5}";

        assertEquals("{\"ratio\":1.5,\"score\":126}", signer.canonicalize(body));
    }

    @Test
    void canonical_preserva_unicode_sin_escapar() {
        String body = "{\"name\":\"José ñ\"}";

        assertEquals("{\"name\":\"José ñ\"}", signer.canonicalize(body));
    }

    @Test
    void canonical_json_invalido_lanza_excepcion_y_verify_rechaza() {
        String roto = "{\"a\":}";

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> signer.canonicalize(roto));

        assertFalse(signer.verify(roto, signer.sign("{}"), timestamp()));
    }

    @Test
    void secret_en_blanco_todo_se_rechaza() {
        DiditWebhookSigner sinSecret = new DiditWebhookSigner("", Clock.fixed(NOW, ZoneOffset.UTC));
        String body = "{\"event_id\":\"e1\"}";

        assertFalse(sinSecret.verify(body, sinSecret.sign(sinSecret.canonicalize(body)), timestamp()));
    }
}
