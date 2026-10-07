package com.openlab.mx.share.notification.service;

import com.openlab.mx.share.notification.entity.Notification;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.repository.NotificationRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Notificaciones in-app (spec §5.4): una fila por destinatario, nacen sin leer.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification notify(User to, NotificationType type, String message, String link) {
        return notificationRepository.save(Notification.builder()
                .user(to)
                .type(type)
                .message(message)
                .link(link)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Una notificación por destinatario. @return cuántas se crearon. */
    public int notifyMassive(Collection<User> tos, NotificationType type, String message, String link) {
        List<Notification> notifications = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        tos.forEach(to -> notifications.add(Notification.builder()
                .user(to)
                .type(type)
                .message(message)
                .link(link)
                .read(false)
                .createdAt(now)
                .build()));
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    public java.util.List<Notification> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Notification markRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new DomainException("Aviso no encontrado"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
