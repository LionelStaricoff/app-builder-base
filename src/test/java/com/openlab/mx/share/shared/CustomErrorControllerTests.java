package com.openlab.mx.share.shared;

import com.openlab.mx.share.shared.controller.CustomErrorController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /error nunca debe dejar al usuario varado: cualquier error vuelve al feed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomErrorControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomErrorController errorController;

    @Test
    void elErrorVuelveAlFeed() {
        assertEquals("redirect:/feed", errorController.handleError());
    }

    @Test
    void errorAceptaLosMetodosConLosQuePuedeLlegarSpring() throws Exception {
        // Un GET (ruta inexistente) y un POST fallido: el reenvío a /error
        // conserva el método original del pedido.
        //
        // Ojo: en producción ese reenvío es un FORWARD interno (no un pedido
        // nuevo), así que no vuelve a pasar por el filtro de CSRF. Acá el
        // pedido va directo al endpoint, por eso el POST lleva token.
        mockMvc.perform(get("/error")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
        mockMvc.perform(post("/error").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
        mockMvc.perform(put("/error").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
    }
}
