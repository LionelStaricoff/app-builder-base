package com.openlab.mx.movo.notification;

import com.openlab.mx.movo.notification.entity.Notification;
import com.openlab.mx.movo.notification.entity.NotificationType;
import com.openlab.mx.movo.notification.repository.NotificationRepository;
import com.openlab.mx.movo.shared.TestIdentityGateConfig;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Badge de avisos no leídos en el bottom nav (fragment layout :: nav).
 *
 * El nav se renderiza en todas las páginas con sesión, así que el conteo
 * viaja como atributo de modelo global y el fragmento lo pinta como
 * .nav-badge con aria-label descriptivo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestIdentityGateConfig.class)
class UnreadBadgeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void limpiarAvisos() {
        notificationRepository.deleteAll();
    }

    @Test
    void badgeMuestraConteoDeNoLeidas() throws Exception {
        User u = crearUsuario();
        avisar(u, false);
        avisar(u, false);

        mockMvc.perform(get("/notifications").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nav-badge")))
                .andExpect(content().string(containsString("aria-label=\"2 avisos sin leer\"")));
    }

    @Test
    void leidasNoCuentan() throws Exception {
        User u = crearUsuario();
        avisar(u, true);   // leída: no suma
        avisar(u, false);  // sin leer: 1

        mockMvc.perform(get("/notifications").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"1 aviso sin leer\"")));
    }

    @Test
    void sinNoLeidasNoHayBadge() throws Exception {
        User u = crearUsuario();
        avisar(u, true);

        mockMvc.perform(get("/notifications").with(user(u.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("nav-badge"))));
    }

    private User crearUsuario() {
        String base = "u-badge-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(base)
                .email(base + "@x.com")
                .passwordHash("{noop}Clave1234!")
                .birthDate(LocalDate.of(1995, 1, 1))
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
    }

    private void avisar(User to, boolean leida) {
        notificationRepository.save(Notification.builder()
                .user(to)
                .type(NotificationType.RESERVA_CONFIRMADA)
                .message("Aviso de prueba")
                .link("/notifications")
                .read(leida)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
