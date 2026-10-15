package com.openlab.mx.share.shared;

import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Usuario autenticado actual + utilidades de retorno PRG.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public User get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    public User require() {
        User user = get();
        if (user == null) {
            throw new com.openlab.mx.share.shared.exception.DomainException("Usuario no encontrado");
        }
        return user;
    }

    /** Path local del request actual (para PRG de regreso), o fallback. */
    public static String backOr(String fallback) {
        return "redirect:" + refererPathOr(fallback);
    }

    /**
     * Path+query del header Referer si es un path local, o fallback.
     * Rechaza referers externos (path que no empieza con / o que empieza //).
     */
    public static String refererPathOr(String fallback) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String referer = request.getHeader("Referer");
            if (referer != null) {
                try {
                    java.net.URI uri = java.net.URI.create(referer);
                    String path = uri.getPath();
                    if (path != null && path.startsWith("/") && !path.startsWith("//")) {
                        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
                        return path + query;
                    }
                } catch (IllegalArgumentException ignored) {
                    // referer inválido: fallback
                }
            }
        }
        return fallback;
    }
}
