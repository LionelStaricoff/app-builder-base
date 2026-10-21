package com.openlab.mx.share.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los recursos de terceros (Leaflet, SockJS, STOMP) están vendorizados en
 * static/vendor para no depender de un CDN en runtime y para que la
 * integridad del archivo la controle el repo.
 *
 * <p>Este test se asegura de que sigan siendo servibles: si alguien borra un
 * archivo o cambia la ruta, el mapa o el chat se rompen en silencio (el
 * front solo avisa por consola).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VendorAssetsTests {

    private static final String[] RECURSOS = {
            "/vendor/leaflet/leaflet.css",
            "/vendor/leaflet/leaflet.js",
            "/vendor/sockjs/sockjs.min.js",
            "/vendor/stomp/stomp.min.js"
    };

    @Autowired
    private MockMvc mockMvc;

    @Test
    void losRecursosVendorSeSirvenSinSesion() throws Exception {
        for (String recurso : RECURSOS) {
            mockMvc.perform(get(recurso)).andExpect(status().isOk());
        }
    }

    @Test
    void todasLasImagenesQuePideElCssDeLeafletExisten() throws Exception {
        String css = new String(
                getClass().getResourceAsStream("/static/vendor/leaflet/leaflet.css").readAllBytes(),
                StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("url\\(([^)]+)\\)").matcher(css);
        Set<String> imagenes = new LinkedHashSet<>();
        while (matcher.find()) {
            String ref = matcher.group(1).replace("\"", "").replace("'", "");
            if (ref.endsWith(".png")) {
                imagenes.add(ref);
            }
        }

        assertTrue(!imagenes.isEmpty(), "el CSS de Leaflet deberia referenciar imagenes");
        for (String imagen : imagenes) {
            // Las referencias del CSS son relativas al propio archivo.
            mockMvc.perform(get("/vendor/leaflet/" + imagen)).andExpect(status().isOk());
        }
    }

    @Test
    void losMarcadoresDelMapaTienenSusIconos() throws Exception {
        for (String icono : new String[]{
                "marker-icon.png", "marker-icon-2x.png", "marker-shadow.png",
                "layers.png", "layers-2x.png"}) {
            mockMvc.perform(get("/vendor/leaflet/images/" + icono)).andExpect(status().isOk());
        }
    }
}
