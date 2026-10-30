package com.openlab.mx.movo.reservation.service;

import com.openlab.mx.movo.notification.entity.NotificationType;
import com.openlab.mx.movo.notification.service.NotificationService;
import com.openlab.mx.movo.payment.service.ReservationConfirmationPort;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstantBookConfirmationAdapter implements ReservationConfirmationPort {
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    @Override
    public void confirmarAlPagar(Long reservationId) {
        Reservation reserva = reservationRepository.findById(reservationId).orElse(null);
        if (reserva == null || reserva.getStatus() != ReservationStatus.PENDIENTE) {
            return;
        }
        reserva.setStatus(ReservationStatus.CONFIRMADA);
        reservationRepository.save(reserva);

        var experiencia = reserva.getInstance().getExperience();
        String nombre = experiencia.getName();
        if (reserva.getUser() != null) {
            notificationService.notify(reserva.getUser(), NotificationType.RESERVA_CONFIRMADA,
                    "Tu reserva en " + nombre + " está confirmada",
                    "/reservations");
        }
        if (experiencia.getCreator() != null && reserva.getUser() != null) {
            notificationService.notify(experiencia.getCreator(), NotificationType.RESERVA_CONFIRMADA,
                    reserva.getUser().getUsername() + " reservó " + reserva.getAttendees()
                            + " lugar(es) en " + nombre,
                    "/reservations");
        }
    }
}
