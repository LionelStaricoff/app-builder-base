
package com.openlab.mx.share;
import org.junit.jupiter.api.Disabled;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresión de humo en servidor real (Tomcat + HTTP real): los flujos multipart
 * con token CSRF en el campo oculto tienen que funcionar igual que en el
 * navegador. MockMvc resuelve los parámetros del multipart por su cuenta y no
 * cubre este camino, así que acá se habla HTTP contra el puerto aleatorio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(com.openlab.mx.share.shared.TestIdentityGateConfig.class)
class MultipartRealServerTests {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager())
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String base() {
        return "http://localhost:" + port;
    }

    private String tokenDe(String html) {
        Matcher m = CSRF.matcher(html);
        assertTrue(m.find(), "la página renderizada debe incluir el token CSRF oculto");
        return m.group(1);
    }

    private String get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpResponse<String> postForm(String path, String token, String... campos) throws Exception {
        StringBuilder body = new StringBuilder("_csrf=" + java.net.URLEncoder.encode(token, "UTF-8"));
        for (int i = 0; i < campos.length; i += 2) {
            body.append('&').append(campos[i]).append('=')
                    .append(java.net.URLEncoder.encode(campos[i + 1], "UTF-8"));
        }
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Body multipart real: campos de texto + (opcional) parte de archivo. */
    private byte[] multipartBody(String boundary, LinkedHashMap<String, String> campos,
                                 String fileField, String fileName, byte[] fileBytes) {
        StringBuilder sb = new StringBuilder();
        for (var e : campos.entrySet()) {
            sb.append("--").append(boundary).append("\r\n")
              .append("Content-Disposition: form-data; name=\"").append(e.getKey()).append("\"\r\n\r\n")
              .append(e.getValue()).append("\r\n");
        }
        if (fileField != null) {
            sb.append("--").append(boundary).append("\r\n")
              .append("Content-Disposition: form-data; name=\"").append(fileField)
              .append("\"; filename=\"").append(fileName).append("\"\r\n")
              .append("Content-Type: image/png\r\n\r\n");
            sb.append(new String(fileBytes, java.nio.charset.StandardCharsets.ISO_8859_1));
            sb.append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private HttpResponse<String> postMultipart(String path, String boundary, byte[] body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String registrarYLogin() throws Exception {
        String user = "mt-" + UUID.randomUUID().toString().substring(0, 8);
        var registro = postForm("/register", tokenDe(get("/register")),
                "username", user, "email", user + "@x.com", "password", "Clave1234!",
                "birthDate", "1995-04-12");
        assertEquals(302, registro.statusCode(), "el registro debe redirigir a /login");
        var login = postForm("/login", tokenDe(get("/login")), "username", user, "password", "Clave1234!");
        assertEquals(302, login.statusCode(), "el login debe redirigir");
        return user;
    }

    @Test
    void crearExperienciaConMultipartFuncionaEnServidorReal() throws Exception {
        String user = registrarYLogin();
        // /settings siempre renderiza el input _csrf (el feed no, si no hay cards).
        String token = tokenDe(get("/settings"));

        String boundary = "----smoke" + UUID.randomUUID();
        LinkedHashMap<String, String> campos = new LinkedHashMap<>();
        campos.put("_csrf", token);
        campos.put("name", "Viaje multipart " + user);
        campos.put("description", "Creado con multipart real");
        campos.put("durationMinutes", "120");
        campos.put("minCapacity", "2");
        campos.put("maxCapacity", "8");
        campos.put("price", "190.00");
        campos.put("currencyCode", "MXN");
        campos.put("category", "CULTURAL");
        campos.put("recurrenceType", "UNIQUE");
        campos.put("meetingLat", "19.4326");
        campos.put("meetingLng", "-99.1332");
        campos.put("schedules[0].date", LocalDate.now().plusDays(30).toString());
        campos.put("schedules[0].startTime", "10:00");
        campos.put("schedules[0].endTime", "12:00");

        var res = postMultipart("/experiences", boundary,
                multipartBody(boundary, campos, "images", "f.png", "abc".getBytes()));

        assertEquals(302, res.statusCode(),
                "crear viaje con multipart debe redirigir (hoy cae en 403 → /feed): " + res.body());
        String location = res.headers().firstValue("Location").orElse("");
        assertTrue(location.contains("/experiences/"),
                "debe ir a la experiencia creada, no al feed: " + location);
    }

    @Test
    void updateDeConfiguracionConMultipartGuardaPaisMoneda() throws Exception {
        String user = registrarYLogin();
        String token = tokenDe(get("/settings"));

        String boundary = "----smoke" + UUID.randomUUID();
        LinkedHashMap<String, String> campos = new LinkedHashMap<>();
        campos.put("_csrf", token);
        campos.put("username", user);
        campos.put("country", "Argentina");
        campos.put("currencyCode", "ARS");

        var res = postMultipart("/settings/update", boundary,
                multipartBody(boundary, campos, null, null, null));

        assertEquals(302, res.statusCode(),
                "guardar config con multipart debe redirigir: " + res.body());
        assertTrue(res.headers().firstValue("Location").orElse("").contains("/settings"),
                "debe volver a /settings: " + res.headers().firstValue("Location"));

        // Relectura como la vería el navegador: el select de país sale en Argentina.
        String html = get("/settings");
        assertTrue(html.contains("selected=\"selected\">Argentina"),
                "el país guardado se muestra seleccionado en /settings");
    }
}