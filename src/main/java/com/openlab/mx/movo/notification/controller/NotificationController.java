package com.openlab.mx.movo.notification.controller;

import com.openlab.mx.movo.notification.service.NotificationService;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Avisos in-app (spec §5.4): listado y marcar como leído.
 */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping("/notifications")
    public String list(Model model) {
        User user = currentUser.require();
        model.addAttribute("notificaciones", notificationService.list(user.getId()));
        return "notifications/notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return CurrentUser.backOr("/notifications");
    }
}
