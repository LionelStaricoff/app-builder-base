package com.openlab.mx.share.shared.config;

import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * El rol se promueve por webhook (sin sesión del usuario), así que la
 * authority guardada en la sesión puede quedar vieja. Este filtro la
 * rederiva de la DB en cada request.
 */
@ExtendWith(MockitoExtension.class)
class RoleRefreshFilterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleRefreshFilter filter;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void sesionCon(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        username, "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private String autoridadActual() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .iterator().next().getAuthority();
    }

    private void correr(String path) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(),
                (req, res) -> { });
    }

    @Test
    void refresca_la_authority_cuando_el_rol_cambio_en_la_db() throws Exception {
        sesionCon("nuevo-creador", "ROLE_USER");
        when(userRepository.findByUsername("nuevo-creador")).thenReturn(Optional.of(
                User.builder().username("nuevo-creador").role(Role.CREATOR).build()));

        correr("/feed");

        assertEquals("ROLE_CREATOR", autoridadActual(),
                "el KYC promueve en la DB: la sesión no puede seguir diciendo ROLE_USER");
    }

    @Test
    void no_toca_nada_si_la_authority_ya_coincide() throws Exception {
        sesionCon("ya-creador", "ROLE_CREATOR");
        when(userRepository.findByUsername("ya-creador")).thenReturn(Optional.of(
                User.builder().username("ya-creador").role(Role.CREATOR).build()));

        correr("/feed");

        assertEquals("ROLE_CREATOR", autoridadActual());
    }

    @Test
    void ignora_recursos_estaticos_sin_consultar_la_db() throws Exception {
        sesionCon("nuevo-creador", "ROLE_USER");

        correr("/css/app.css");

        assertEquals("ROLE_USER", autoridadActual());
    }

    @Test
    void sin_autenticacion_no_hace_nada() throws Exception {
        correr("/feed");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
