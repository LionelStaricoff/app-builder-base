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

    /** Única reserva (cualquier estado) de un usuario en una fecha — la usa el test de post-pago. */
    java.util.Optional<Reservation> findFirstByInstanceIdAndUserId(Long instanceId, Long userId);

    List<Reservation> findByInstanceExperienceCreatorIdAndStatus(Long creatorId, ReservationStatus status);

    boolean existsByInstanceIdAndUserIdAndStatus(Long instanceId, Long userId, ReservationStatus status);

    /** La instancia tiene reservas vigentes (las canceladas liberan el slot). */
    boolean existsByInstanceIdAndStatusNot(Long instanceId, ReservationStatus status);

    /**
     * "Quiénes van" del detalle: personas con reserva CONFIRMADA en alguna fecha
     * futura de la experiencia, sin repetir a quien reservó varias veces.
     *
     * <p>Solo {@code CONFIRMADA}: las PENDIENTES todavía no pagaron y las
     * CANCELADAS liberaron su lugar, así que mostrarlas sería mentirle a quien
     * está por comprar. Se proyecta a DTO para no arrastrar la entidad entera.
     */
    @Query("""
            select distinct new com.openlab.mx.share.reservation.dto.ExperienceGoerDTO(
                u.username, u.avatarBase64)
            from Reservation r
            join r.instance i
            join r.user u
            where i.experience.id = :experienceId
              and r.status = :status
              and i.date >= :today
            order by u.username
            """)
    List<com.openlab.mx.share.reservation.dto.ExperienceGoerDTO> goersForExperience(
            @Param("experienceId") Long experienceId,
            @Param("status") ReservationStatus status,
            @Param("today") java.time.LocalDate today);
}
