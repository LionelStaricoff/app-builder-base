package com.openlab.mx.movo.notification.controller.api;

import com.openlab.mx.movo.notification.entity.Notification;
import com.openlab.mx.movo.notification.service.NotificationService;
import com.openlab.mx.movo.shared.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API REST read-only + mark-read de notificaciones (para front separado).
 * No altera la vista Thymeleaf existente.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationApiController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    public record NotificationResponse(
            Long id,
            String type,
            String message,
            String link,
            boolean read,
            java.time.LocalDateTime createdAt) {
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list() {
        Long userId = currentUser.require().getId();
        List<NotificationResponse> body = notificationService.list(userId).stream()
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getType() == null ? null : n.getType().name(),
                        n.getMessage(),
                        n.getLink(),
                        n.isRead(),
                        n.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> read(@PathVariable Long id) {
        Notification n = notificationService.markRead(id);
        return ResponseEntity.ok(new NotificationResponse(
                n.getId(),
                n.getType() == null ? null : n.getType().name(),
                n.getMessage(),
                n.getLink(),
                n.isRead(),
                n.getCreatedAt()));
    }
}
