package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.dto.ExperienceDateDTO;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.InstanceStatus;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.reservation.service.RefundPolicy;
import com.openlab.mx.movo.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Datos de la card del feed que dependen de la fecha elegida.
 *
 * <p>La card no crea reservas: solo muestra fechas futuras con sus cupos y
 * avatares. Toda la lectura se resuelve en una query de instancias y una de
 * reservas, para no multiplicar consultas por cada fecha.
 */
@Service
@RequiredArgsConstructor
public class ExperienceCardService {

    private final ExperienceRepository experienceRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final RefundPolicy refundPolicy;

    /** Fechas futuras reservables, en orden, con cupos y avatares de cada una. */
    @Transactional(readOnly = true)
    public List<ExperienceDateDTO> dates(Long experienceId) {
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new DomainException("Experiencia no encontrada"));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<ExperienceInstance> instances = instanceRepository
                .findByExperienceIdAndDateGreaterThanEqualOrderByDateAsc(experienceId, today).stream()
                .filter(this::esReservable)
                .toList();
        if (instances.isEmpty()) {
            return List.of();
        }
        List<Long> ids = instances.stream().map(ExperienceInstance::getId).toList();
        Map<Long, List<Reservation>> reservasPorFecha = reservationRepository
                .findByInstanceIdInAndStatusNot(ids, ReservationStatus.CANCELADO).stream()
                .filter(r -> r.getInstance() != null && r.getInstance().getId() != null)
                .collect(Collectors.groupingBy(r -> r.getInstance().getId()));
        return instances.stream()
                .map(instance -> toDto(experience, instance,
                        reservasPorFecha.getOrDefault(instance.getId(), List.of())))
                .toList();
    }

    /** Disponibilidad de UNA fecha, para refrescar el bloque de cupos sin recargar. */
    @Transactional(readOnly = true)
    public ExperienceDateDTO availability(Long experienceId, Long instanceId) {
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new DomainException("Experiencia no encontrada"));
        ExperienceInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
        boolean deLaExperiencia = instance.getExperience() != null
                && experienceId.equals(instance.getExperience().getId());
        if (!deLaExperiencia || !esReservable(instance)) {
            throw new DomainException("Fecha no encontrada");
        }
        return toDto(experience, instance,
                reservationRepository.findByInstanceIdAndStatusNot(instanceId, ReservationStatus.CANCELADO));
    }

    private boolean esReservable(ExperienceInstance instance) {
        return instance.getStatus() == InstanceStatus.SCHEDULED
                && !instance.isArchived()
                && !instance.getDate().isBefore(LocalDate.now(ZoneId.systemDefault()));
    }

    private ExperienceDateDTO toDto(Experience experience, ExperienceInstance instance,
                                    List<Reservation> reservations) {
        int reserved = reservations.stream().mapToInt(Reservation::getAttendees).sum();
        List<String> avatars = reservations.stream()
                .map(r -> r.getUser() == null ? null : r.getUser().getAvatarBase64())
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .limit(experience.getMaxCapacity())
                .toList();
        int max = experience.getMaxCapacity();
        return new ExperienceDateDTO(
                instance.getId(),
                instance.getDate(),
                instance.getStartTime(),
                max,
                Math.max(0, max - reserved),
                reserved,
                avatars,
                refundPolicy.percentFor(instance));
    }
}
