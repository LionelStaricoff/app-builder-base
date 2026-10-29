package com.openlab.mx.movo.chat;

import com.openlab.mx.movo.chat.entity.ChatMessage;
import com.openlab.mx.movo.chat.repository.ChatMessageRepository;
import com.openlab.mx.movo.chat.service.ChatAuthorizationService;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAuthorizationServiceTest {

    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatAuthorizationService chatAuthorizationService;

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private ExperienceInstance instancia(boolean archivada) {
        Experience experience = Experience.builder()
                .id(1L)
                .creator(user(1L))
                .name("Tour")
                .build();
        return ExperienceInstance.builder()
                .id(10L)
                .experience(experience)
                .date(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .status(InstanceStatus.SCHEDULED)
                .archived(archivada)
                .build();
    }

    @Test
    void testDuenoPuede() {
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia(false)));

        assertTrue(chatAuthorizationService.canAccess(10L, user(1L)));
    }

    @Test
    void testInscritoConfirmadoPuede() {
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia(false)));
        when(reservationRepository.existsByInstanceIdAndUserIdAndStatus(10L, 200L, ReservationStatus.CONFIRMADA))
                .thenReturn(true);

        assertTrue(chatAuthorizationService.canAccess(10L, user(200L)));
    }

    @Test
    void testNoInscritoNoPuede() {
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia(false)));
        when(reservationRepository.existsByInstanceIdAndUserIdAndStatus(10L, 300L, ReservationStatus.CONFIRMADA))
                .thenReturn(false);

        assertFalse(chatAuthorizationService.canAccess(10L, user(300L)));
        assertThrows(AccessDeniedException.class,
                () -> chatAuthorizationService.sendMessage(10L, user(300L), "hola"));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void testArchivadoNoAdmiteMensajes() {
        when(instanceRepository.findById(10L)).thenReturn(Optional.of(instancia(true)));

        assertTrue(chatAuthorizationService.canAccess(10L, user(1L)), "el dueño puede leer");
        assertThrows(AccessDeniedException.class,
                () -> chatAuthorizationService.sendMessage(10L, user(1L), "hola"));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }
}
