package com.openlab.mx.share.shared.controller;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Redirección directa: cualquier ruta sin handler (404) o error
 * vuelve al Feed sin mostrar página intermedia.
 * Decisión del proyecto: se prioriza que el usuario nunca quede varado.
 */
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError() {
        return "redirect:/feed";
    }
}
