package com.openlab.mx.share.shared.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    /**
     * PRG con mensaje flash de vuelta a donde vino el usuario (spec §8).
     * Devolver la excepción como return value haría que Spring buscara una
     * vista con el nombre de la URL y respondiera 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooBig(MaxUploadSizeExceededException e, RedirectAttributes redirectAttributes) {
        log.warn("Upload rechazado por tamaño: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("error",
                "La imagen es demasiado grande (máximo 5 MB por foto). Bajá la resolución e intentá de nuevo.");
        return com.openlab.mx.share.shared.CurrentUser.backOr("/my-experiences");
    }

    @ExceptionHandler(DomainException.class)
    public String domain(DomainException e, RedirectAttributes redirectAttributes) {
        log.warn("DomainException: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return com.openlab.mx.share.shared.CurrentUser.backOr("/feed");
    }
}
