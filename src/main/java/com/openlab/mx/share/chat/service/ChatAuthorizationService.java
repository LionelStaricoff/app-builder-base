package com.openlab.mx.share.chat.service;

import com.openlab.mx.share.chat.entity.ChatMessage;
import com.openlab.mx.share.chat.repository.ChatMessageRepository;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Autorización del chat por instancia (spec §5.6, §7): entra el dueño o
 * quien tiene una reserva CONFIRMADA; una fecha archivada es solo lectura.
 */
@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final ExperienceInstanceRepository instanceRepository;
    private final ExperienceRepository experienceRepository;
    private final ReservationRepository reservationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public boolean canAccess(Long instanceId, User u) {
        ExperienceInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || u == null) {
            return false;
        }
        User creador = instance.getExperience() != null ? instance.getExperience().getCreator() : null;
        if (creador != null && creador.getId() != null && creador.getId().equals(u.getId())) {
            return true;
        }
        return reservationRepository.existsByInstanceIdAndUserIdAndStatus(
                instanceId, u.getId(), ReservationStatus.CONFIRMADA);
    }

    /** Persiste y publica el mensaje (el caller lo manda a /topic). */
    public ChatMessage sendMessage(Long instanceId, User u, String text) {
        ExperienceInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
        if (u == null || !canAccess(instanceId, u)) {
            throw new AccessDeniedException("No podés escribir en ese chat");
        }
        if (instance.isArchived()) {
            throw new AccessDeniedException("La fecha fue cancelada: el chat está en solo-lectura");
        }
        if (text == null || text.isBlank()) {
            throw new DomainException("El mensaje no puede estar vacío");
        }
        return chatMessageRepository.save(ChatMessage.builder()
                .instance(instance)
                .user(u)
                .text(text.trim())
                .createdAt(LocalDateTime.now())
                .build());
    }

    public List<ChatMessage> history(Long instanceId, User u) {
        if (!canAccess(instanceId, u)) {
            throw new AccessDeniedException("No podés ver ese chat");
        }
        return chatMessageRepository.findByInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    /** Grupos a los que el usuario entra: reservas confirmadas + sus propias experiencias. */
    public List<ExperienceInstance> groupsFor(User u) {
        if (u == null || u.getId() == null) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        reservationRepository.findByUserIdAndStatus(u.getId(), ReservationStatus.CONFIRMADA)
                .forEach(r -> ids.add(r.getInstance().getId()));
        experienceRepository.findByCreatorId(u.getId()).forEach(experience ->
                instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId())
                        .forEach(instance -> ids.add(instance.getId())));
        return instanceRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(ExperienceInstance::getDate).reversed())
                .toList();
    }

    public ExperienceInstance instance(Long instanceId, User u) {
        if (!canAccess(instanceId, u)) {
            throw new AccessDeniedException("No podés ver ese chat");
        }
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
    }
}
