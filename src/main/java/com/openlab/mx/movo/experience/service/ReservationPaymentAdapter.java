package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.payment.service.CheckoutReservationPort;
import com.openlab.mx.movo.payment.service.InstanceConfirmedReservationsPort;
import com.openlab.mx.movo.payment.service.ReservationStatusPort;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationPaymentAdapter implements InstanceConfirmedReservationsPort, ReservationStatusPort, CheckoutReservationPort {

    private final ReservationRepository reservationRepository;

    @Override
    public List<Long> confirmedReservationIds(Long instanceId) {
        return reservationRepository.findByInstanceIdAndStatus(instanceId, ReservationStatus.CONFIRMADA)
                .stream().map(Reservation::getId).toList();
    }

    @Override
    public Optional<Long> confirmedInstanceId(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.CONFIRMADA
                || reservation.getInstance() == null) {
            return Optional.empty();
        }
        return Optional.of(reservation.getInstance().getId());
    }

    @Override
    public Optional<Long> reservationOwnerId(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getUser() == null) {
            return Optional.empty();
        }
        return Optional.of(reservation.getUser().getId());
    }

    @Override
    public boolean cancelPendingReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDIENTE) {
            return false;
        }
        reservation.setStatus(ReservationStatus.CANCELADO);
        reservationRepository.save(reservation);
        return true;
    }
}
