package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.dto.FirstStopCountry;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import com.openlab.mx.share.experience.entity.ExperienceStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExperienceStopRepository extends JpaRepository<ExperienceStop, Long> {
    List<ExperienceStop> findByExperienceIdOrderByPositionAscIdAsc(Long experienceId);
    void deleteByExperienceId(Long experienceId);

    /**
     * Una sola query para el filtro por país del feed: país de la primera
     * parada (position = 0) de cada experiencia activa. Sin N+1 por tarjeta.
     */
    @Query("select new com.openlab.mx.share.experience.dto.FirstStopCountry(s.experience.id, s.country) "
            + "from ExperienceStop s where s.position = 0 "
            + "and s.experience.status = :status")
    List<FirstStopCountry> findFirstStopCountries(@Param("status") ExperienceStatus status);

    /**
     * Todas las paradas de experiencias activas para el buscador libre de
     * país y/o ciudad: matchea contra cualquier parada, no solo la primera.
     */
    @Query("select s from ExperienceStop s where s.experience.status = :status")
    List<ExperienceStop> findAllActive(@Param("status") ExperienceStatus status);
}
