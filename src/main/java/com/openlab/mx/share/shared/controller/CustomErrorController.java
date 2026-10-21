package com.openlab.mx.share.shared.controller;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Redirección directa: cualquier ruta sin handler (404) o error
 * vuelve al Feed sin mostrar página intermedia.
 * Decisión del proyecto: se prioriza que el usuario nunca quede varado.
 */
@Controller
public class CustomErrorController implements ErrorController {

    /**
     * Los métodos se declaran explícitos (no un {@code @RequestMapping} pelado)
     * para dejar claro qué acepta el endpoint.
     *
     * <p>Se aceptan todos: cuando Spring reenvía a {@code /error} ante un
     * fallo, el reenvío conserva el método original del pedido (un POST que
     * falla llega como POST). Si se declarara solo GET, un POST fallido
     * rebotaría con 405 en lugar de volver al feed.
     */
    @RequestMapping(value = "/error", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE})
    public String handleError() {
        return "redirect:/feed";
    }
}
