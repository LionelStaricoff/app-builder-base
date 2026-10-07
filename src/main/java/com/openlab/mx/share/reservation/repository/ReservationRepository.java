package com.openlab.mx.share.reservation.repository;

import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Cupos ocupados de una instancia (las canceladas liberan cupo). */
    @Query("select coalesce(sum(r.attendees), 0) from Reservation r " +
            "where r.instance.id = :instanceId " +
            "and r.status <> com.openlab.mx.share.reservation.entity.ReservationStatus.CANCELADO")
    long countAttendees(@Param("instanceId") Long instanceId);

    List<Reservation> findByInstanceIdAndStatus(Long instanceId, ReservationStatus status);

    List<Reservation> findByInstanceIdAndStatusNot(Long instanceId, ReservationStatus status);

    List<Reservation> findByInstanceIdInAndStatus(List<Long> instanceIds, ReservationStatus status);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status);

    Reservation findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<Reservation> findFirstByInstanceIdAndUserIdAndStatus(
            Long instanceId, Long userId, ReservationStatus status);

    List<Reservation> findByInstanceExperienceCreatorIdAndStatus(Long creatorId, ReservationStatus status);

    boolean existsByInstanceIdAndUserIdAndStatus(Long instanceId, Long userId, ReservationStatus status);
}
