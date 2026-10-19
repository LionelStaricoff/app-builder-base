package com.openlab.mx.share.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

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

    /**
     * Gate de identidad verificada (Didit): en vez del PRG genérico, se
     * manda al usuario a /verificacion con returnUrl de regreso. Los
     * endpoints /api que disparen este gate reciben el mismo redirect
     * (hoy los protegidos son form-POST de MVC).
     */
    @ExceptionHandler(UnverifiedIdentityException.class)
    public Object unverifiedIdentity(UnverifiedIdentityException e, HttpServletRequest request) {
        if (isXhr(request)) {
            // Un fetch no puede "seguir" un redirect HTML: devolvemos JSON con el
            // código del gate para que el front muestre el mensaje / redirija solo.
            log.warn("[gate] UnverifiedIdentityException en {} (XHR) → 409 JSON IDENTITY_NOT_VERIFIED",
                    request.getRequestURI());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "IDENTITY_NOT_VERIFIED", "message", mensaje(e)));
        }
        log.warn("[gate] UnverifiedIdentityException en {} → redirect /verificacion (referer={})",
                request.getRequestURI(), request.getHeader("Referer"));
        String returnUrl = com.openlab.mx.share.shared.CurrentUser.refererPathOr("/feed");
        String encoded = java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/verificacion?returnUrl=" + encoded;
    }

    /** VERIFIED es terminal: redirigir a la propia página de verificación. */
    @ExceptionHandler(VerificationAlreadyCompletedException.class)
    public String alreadyCompleted(VerificationAlreadyCompletedException e) {
        log.info("Verificación ya completada: {}", e.getMessage());
        return "redirect:/verificacion";
    }

    @ExceptionHandler(DomainException.class)
    public Object domain(DomainException e, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (isXhr(request)) {
            // PRG con flash no aplica a un fetch: respuesta JSON con el mensaje.
            log.warn("[domain] {} en {} (XHR) → 409 JSON", e.getClass().getSimpleName(), request.getRequestURI());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", mensaje(e)));
        }
        String destino = com.openlab.mx.share.shared.CurrentUser.refererPathOr("/feed");
        log.warn("[domain] {} en {} → redirect {} (referer={}, xhr=false): {}",
                e.getClass().getSimpleName(), request.getRequestURI(), destino,
                request.getHeader("Referer"), e.getMessage());
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:" + destino;
    }

    /**
     * Convención del proyecto: los fetch mandan X-Requested-With: XMLHttpRequest
     * (ver didit-verification.js). Para esos pedidos el PRG con redirect/flash
     * rompe al front (fetch sigue el redirect y res.json() recibe HTML).
     */
    private static boolean isXhr(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private static String mensaje(DomainException e) {
        return e.getMessage() != null ? e.getMessage() : "Solicitud rechazada";
    }
}
