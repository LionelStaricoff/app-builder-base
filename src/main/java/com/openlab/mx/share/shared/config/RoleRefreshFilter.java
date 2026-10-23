package com.openlab.mx.share.shared.config;

import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * El rol vive en la DB, pero la sesión guarda las authorities del momento del
 * login. Desde que completar el KYC promueve USER → CREATOR por webhook (sin
 * sesión del usuario), esa foto queda vieja y los {@code @PreAuthorize}
 * siguen denegando hasta el próximo login. El usuario verificaría su
 * identidad y aun así no podría publicar: un callejón sin salida.
 *
 * <p>Este filtro relee el rol una vez por request y, si cambió, reemplaza las
 * authorities del {@link SecurityContextHolder}. No persiste nada en la
 * sesión a propósito: se recalcula en cada request, así no hay estado que se
 * desincronice ni carrera con la llegada del webhook.
 *
 * <p>Corre después de la cadena de Spring Security (que carga el contexto) y
 * antes del DispatcherServlet (que evalúa {@code @PreAuthorize}).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RoleRefreshFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    /** Recursos estáticos: no hay autorización que refrescar y son la mayoría del tráfico. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.startsWith("/icons/") || path.startsWith("/vendor/")
                || path.equals("/error")
                || path.equals("/manifest.webmanifest") || path.equals("/sw.js");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            userRepository.findByUsername(auth.getName()).ifPresent(user -> {
                String esperada = "ROLE_" + user.getRole().name();
                boolean coincide = auth.getAuthorities().stream()
                        .anyMatch(a -> esperada.equals(a.getAuthority()));
                if (!coincide) {
                    var actualizada = UsernamePasswordAuthenticationToken.authenticated(
                            auth.getPrincipal(), auth.getCredentials(),
                            List.of(new SimpleGrantedAuthority(esperada)));
                    actualizada.setDetails(auth.getDetails());
                    SecurityContextHolder.getContext().setAuthentication(actualizada);
                    log.info("Authorities de {} refrescadas a {}", user.getUsername(), esperada);
                }
            });
        }
        chain.doFilter(request, response);
    }
}
