package com.openlab.mx.movo.shared.controller;

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
 * resuelve el Content-Type por Servlet mime-mappings y cae a
 * {@code application/octet-stream} para {@code .webmanifest}.
 * El mapping explícito tiene precedencia sobre el resource handler de
 * {@code /**}, así que este método gana siempre, en tests y en runtime.
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
