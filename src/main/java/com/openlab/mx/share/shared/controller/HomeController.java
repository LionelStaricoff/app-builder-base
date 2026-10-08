package com.openlab.mx.share.shared.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La raíz no tiene vista propia: manda al Feed.
 * Si no hay sesión, SecurityConfig intercepta antes y manda a /login.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/feed";
    }
}
