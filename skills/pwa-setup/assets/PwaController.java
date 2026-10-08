package {{BASE_PACKAGE}}.shared.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Sirve el manifest PWA con el Content-Type del estándar
 * ({@code application/manifest+json}).
 *
 * <p>Los estáticos se sirven vía {@code ResourceHttpRequestHandler}, que
 * resuelve el Content-Type por mime-mappings del contenedor y cae a
 * {@code application/octet-stream} para {@code .webmanifest}. Ni los
 * {@code MimeMappings} del servidor ni los {@code media-types} de
 * content-negotiation lo corrigen (verificado empíricamente).
 * El mapping explícito tiene precedencia sobre el resource handler de
 * {@code /**}: gana siempre, en tests (MockMvc) y en runtime.
 *
 * <p>Boot 4: las clases del servidor viven en
 * {@code org.springframework.boot.web.server[.servlet]} (artefacto
 * {@code spring-boot-web-server}), no en {@code web.servlet.server}.
 *
 * <p>Recordar: permitir {@code /manifest.webmanifest} en la config de
 * seguridad si las demás rutas exigen autenticación.
 */
@Controller
public class PwaController {

    private static final MediaType MANIFEST_JSON = MediaType.parseMediaType("application/manifest+json");

    private final Resource manifest = new ClassPathResource("static/manifest.webmanifest");

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    @ResponseBody
    public ResponseEntity<Resource> manifest() {
        return ResponseEntity.ok().contentType(MANIFEST_JSON).body(manifest);
    }
}
