package com.openlab.mx.share.shared.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manejo central de errores de dominio (spec §8): 403 para recursos
 * ajenos, PRG con mensaje flash para el resto.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotOwnerException.class)
    public void notOwner(NotOwnerException e, HttpServletResponse response) throws java.io.IOException {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public DomainException domain(DomainException e, RedirectAttributes redirectAttributes) {
        log.warn("DomainException: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return e;
    }
}
