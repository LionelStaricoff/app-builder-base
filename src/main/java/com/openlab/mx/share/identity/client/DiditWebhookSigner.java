package com.openlab.mx.share.identity.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verificación de firma de webhooks de Didit (X-Signature-V2 + X-Timestamp),
 * según la spec oficial de Didit:
 *
 * <ul>
 *   <li>HMAC-SHA256(secret, JSON canónico) en hex, comparación constant-time.</li>
 *   <li>JSON canónico: keys ordenadas recursivamente, separadores compactos,
 *       Unicode sin escapar (ensure_ascii=False) y floats enteros a long.</li>
 *   <li>Rechaza si abs(now - X-Timestamp) > 300s (replay).</li>
 * </ul>
 *
 * Serializa el canónico a mano (Gson escapa Unicode por defecto y con
 * separadores con espacio; acá necesitamos bytes exactos).
 */
public class DiditWebhookSigner {

    private static final long MAX_SKEW_SECONDS = 300;

    private final String secret;
    private final Clock clock;

    public DiditWebhookSigner(String secret) {
        this(secret, Clock.systemUTC());
    }

    public DiditWebhookSigner(String secret, Clock clock) {
        this.secret = secret == null ? "" : secret;
        this.clock = clock;
    }

    /** JSON canónico de Didit: sort_keys + separadores compactos + Unicode crudo + floats enteros a long. */
    public String canonicalize(String rawJson) {
        if (rawJson == null) {
            throw new IllegalArgumentException("Body nulo");
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(rawJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON inválido para canonicalizar", e);
        }
        if (element == null) {
            throw new IllegalArgumentException("Body sin contenido");
        }
        StringBuilder sb = new StringBuilder(rawJson.length());
        write(element, sb);
        return sb.toString();
    }

    /** HMAC-SHA256 hex (lowercase) sobre el string dado. Sin secret no hay firma posible. */
    public String sign(String data) {
        if (secret.isEmpty()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC", e);
        }
    }

    /**
     * Verificación completa: ventana de 300s + firma V2 sobre el canónico.
     * Cualquier entrada inesperada (JSON roto, headers no numéricos, secret
     * sin configurar) se rechaza en vez de lanzar.
     */
    public boolean verify(String rawBody, String signature, String timestampHeader) {
        if (secret.isBlank() || rawBody == null || signature == null || signature.isBlank()
                || timestampHeader == null) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException _) {
            return false;
        }
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > MAX_SKEW_SECONDS) {
            return false;
        }
        String expected;
        try {
            expected = sign(canonicalize(rawBody));
        } catch (RuntimeException _) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private void write(JsonElement element, StringBuilder sb) {
        if (element == null || element.isJsonNull()) {
            sb.append("null");
            return;
        }
        if (element.isJsonObject()) {
            writeObject(element.getAsJsonObject(), sb);
            return;
        }
        if (element.isJsonArray()) {
            writeArray(element.getAsJsonArray(), sb);
            return;
        }
        writePrimitive(element.getAsJsonPrimitive(), sb);
    }

    private void writeObject(JsonObject object, StringBuilder sb) {
        sb.append('{');
        TreeMap<String, JsonElement> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(entry.getKey(), sb);
            sb.append(':');
            write(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private void writeArray(JsonArray array, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (JsonElement item : array) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(item, sb);
        }
        sb.append(']');
    }

    private void writePrimitive(JsonPrimitive primitive, StringBuilder sb) {
        if (primitive.isBoolean()) {
            sb.append(primitive.getAsBoolean());
            return;
        }
        if (primitive.isNumber()) {
            BigDecimal value = primitive.getAsBigDecimal().stripTrailingZeros();
            if (value.scale() <= 0) {
                sb.append(value.toBigInteger());
            } else {
                sb.append(value.toPlainString());
            }
            return;
        }
        writeString(primitive.getAsString(), sb);
    }

    /** Escape mínimo: comillas, backslash y controles. Unicode queda crudo (ensure_ascii=False). */
    private void writeString(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
