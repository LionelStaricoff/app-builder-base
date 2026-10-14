package com.openlab.mx.share.reservation.service;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.shared.exception.CupoAgotadoException;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.InstancePastException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Reservas con validación de cupo (spec §5.3). La creación del pago vive
 * en StripeCheckoutService: reserve() solo persiste la reserva PENDIENTE.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ExperienceInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public Reservation reserve(Long instanceId, int attendees, User u) {
        ExperienceInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
        if (isPast(instance)) {
            throw new InstancePastException("Esa fecha ya pasó");
        }
        if (attendees < 1) {
            throw new DomainException("Debe reservar al menos 1 lugar");
        }
        if (instance.getStatus() != InstanceStatus.SCHEDULED) {
            throw new DomainException("Esa fecha no está disponible");
        }

        long ocupados = reservationRepository.countAttendees(instanceId);
        long disponibles = instance.getExperience().getMaxCapacity() - ocupados;
        if (disponibles < attendees) {
            throw new CupoAgotadoException("No quedan lugares disponibles");
        }

        Reservation reservation = Reservation.builder()
                .instance(instance)
                .user(u)
                .attendees(attendees)
                .status(ReservationStatus.PENDIENTE)
                .createdAt(LocalDateTime.now())
                .build();
        reservationRepository.save(reservation);

        return reservation;
    }

    /** Solo el dueño de la experiencia confirma (spec §5.3). */
    @Transactional
    public Reservation confirm(Long reservationId, User creator) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new DomainException("Reserva no encontrada"));
        User dueno = reservation.getInstance().getExperience().getCreator();
        if (creator == null || dueno == null || !dueno.getId().equals(creator.getId())) {
            throw new NotOwnerException();
        }
        reservation.setStatus(ReservationStatus.CONFIRMADA);
        return reservationRepository.save(reservation);
    }

    private boolean isPast(ExperienceInstance instance) {
        LocalDate hoy = LocalDate.now();
        if (instance.getDate().isBefore(hoy)) {
            return true;
        }
        return instance.getDate().equals(hoy) && instance.getEndTime().isBefore(LocalTime.now());
    }
}
