package com.openlab.mx.movo.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El handshake de SockJS.
 *
 * <p>SockJS negocia por HTTP antes de abrir el WebSocket: pide GET /ws/info y,
 * si el navegador no puede usar WebSocket, sigue con POST /ws/{servidor}/
 * {sesion}/xhr. Ese POST lo rechaza el filtro de CSRF si la ruta no esta
 * exenta, y el chat se queda sin conectar (sin error visible en la pagina).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatSockJsHandshakeTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elHandshakeDeSockJsResponde() throws Exception {
        mockMvc.perform(get("/ws/info").with(user("demo_user")))
                .andExpect(status().isOk());
    }

    @Test
    void elTransporteXhrDeSockJsNoLoBloqueaElCsrf() throws Exception {
        // Si el CSRF bloquease la ruta, respondería 403. Con la exención pasa
        // el filtro y es el dispatcher el que dice que no existe esa sesión.
        var res = mockMvc.perform(post("/ws/000/sesion-inexistente/xhr").with(user("demo_user")))
                .andReturn();

        assertNotEquals(403, res.getResponse().getStatus(),
                "el filtro de CSRF está bloqueando el transporte xhr de SockJS");
    }
}
