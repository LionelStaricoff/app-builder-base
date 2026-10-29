package com.openlab.mx.movo.shared.controller;

import com.openlab.mx.movo.notification.repository.NotificationRepository;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Atributos de modelo globales: se agregan a cada vista renderizada
 * sin repetirlos en los controladores.
 *
 * El bottom nav (fragment layout :: nav) vive en casi todas las páginas,
 * así que el conteo de avisos sin leer viaja de acá y el fragmento lo
 * pinta como .nav-badge. Si no hay sesión o el usuario no está en la BD
 * (p. ej. usuarios de prueba), la cuenta es 0: nunca lanza excepción.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CurrentUser currentUser;
    private final NotificationRepository notificationRepository;

    /** Avisos sin leer del usuario de la sesión (0 si no hay usuario). */
    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        User user = currentUser.get();
        if (user == null) {
            return 0;
        }
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }
}
