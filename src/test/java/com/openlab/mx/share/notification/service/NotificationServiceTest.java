package com.openlab.mx.share.notification.service;

import com.openlab.mx.share.notification.entity.Notification;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.repository.NotificationRepository;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    @Test
    void testNotifyPersisteNoLeida() {
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification n = notificationService.notify(user(10L), NotificationType.SOLICITUD_RESERVA,
                "Nuevo pedido de reserva", "/reservations");

        assertEquals(user(10L).getId(), n.getUser().getId());
        assertEquals(NotificationType.SOLICITUD_RESERVA, n.getType());
        assertEquals("Nuevo pedido de reserva", n.getMessage());
        assertEquals("/reservations", n.getLink());
        assertFalse(n.isRead(), "nace sin leer");
    }

    @Test
    void testMasivaCreaUnaPorInscrito() {
        when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int creadas = notificationService.notifyMassive(
                List.of(user(1L), user(2L), user(3L)),
                NotificationType.CANCELACION_REEMBOLSO,
                "La fecha fue cancelada",
                "/feed");

        assertEquals(3, creadas);
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> guardadas = captor.getValue();
        assertEquals(3, guardadas.size());
        assertEquals(3, guardadas.stream().map(n -> n.getUser().getId()).distinct().count());
        guardadas.forEach(n -> assertEquals(NotificationType.CANCELACION_REEMBOLSO, n.getType()));
    }

    @Test
    void testMarcarComoLeida() {
        Notification n = Notification.builder().id(1L).user(user(1L))
                .type(NotificationType.PAGO_LIBERADO)
                .message("Pago liberado")
                .link("/reservations")
                .build();
        when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(n));
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification leida = notificationService.markRead(1L);

        org.junit.jupiter.api.Assertions.assertTrue(leida.isRead());
        verify(notificationRepository).save(n);
    }
}
