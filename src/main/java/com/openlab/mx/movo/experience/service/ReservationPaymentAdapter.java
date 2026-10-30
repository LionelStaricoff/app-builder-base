package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.payment.service.CheckoutReservationPort;
import com.openlab.mx.movo.payment.service.InstanceConfirmedReservationsPort;
import com.openlab.mx.movo.payment.service.ReservationStatusPort;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.reservation.service.ReservationService;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationPaymentAdapter implements InstanceConfirmedReservationsPort, ReservationStatusPort, CheckoutReservationPort {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final UserRepository userRepository;

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

    @Override
    public Optional<CheckoutReservationPort.ReservationCheckoutData> findPendingReservation(Long instanceId, Long userId) {
        return reservationRepository.findFirstByInstanceIdAndUserIdAndStatus(
                instanceId, userId, ReservationStatus.PENDIENTE)
                .map(this::toCheckoutData);
    }

    @Override
    public CheckoutReservationPort.ReservationCheckoutData reserveReservation(Long instanceId, int attendees, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new DomainException("Usuario no encontrado"));
        Reservation reservation = reservationService.reserve(instanceId, attendees, user);
        return toCheckoutData(reservation);
    }

    private CheckoutReservationPort.ReservationCheckoutData toCheckoutData(Reservation reservation) {
        ExperienceInstance instance = reservation.getInstance();
        com.openlab.mx.movo.experience.entity.Experience experience = instance.getExperience();
        return new CheckoutReservationPort.ReservationCheckoutData(
                reservation.getId(),
                reservation.getAttendees(),
                experience.getId(),
                experience.getPrice(),
                experience.getCurrencyCode(),
                experience.getName());
    }
}
