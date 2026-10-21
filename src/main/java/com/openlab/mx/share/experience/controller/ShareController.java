package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.shared.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Datos para compartir un viaje: la hoja de compartir (share-sheet.js) pide
 * la URL canónica + título + descripción y arma los links de todas las redes.
 */
@Controller
@RequiredArgsConstructor
public class ShareController {

    /** Lo que ve cada red al compartir. url = enlace canónico de la experiencia. */
    public record ShareMeta(String url, String title, String description) {
    }

    private final FeedService feedService;

    @GetMapping("/share/{id}/meta")
    @ResponseBody
    public ResponseEntity<ShareMeta> meta(@PathVariable Long id, HttpServletRequest request) {
        Experience experience;
        try {
            experience = feedService.experienceRef(id);
        } catch (DomainException _) {
            return ResponseEntity.notFound().build();
        }
        String base = request.getScheme() + "://" + request.getServerName()
                + puertoNoEstandar(request);
        return ResponseEntity.ok(new ShareMeta(
                base + "/experiences/" + id,
                experience.getName(),
                experience.getDescription()));
    }

    /** 80/443 van implícitos en la URL; cualquier otro puerto se lista (dev en :8080). */
    private String puertoNoEstandar(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean estandar = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return estandar ? "" : ":" + port;
    }
}
