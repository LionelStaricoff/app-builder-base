package com.openlab.mx.share.interest.repository;

import com.openlab.mx.share.interest.entity.UserInterest;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    /**
     * Intereses del usuario con el subrubro ya cargado. El {@code @EntityGraph}
     * evita el N+1 de la vista y que Thymeleaf toque un proxy fuera de sesión.
     */
    @EntityGraph(attributePaths = "subrubro")
    List<UserInterest> findByUserId(Long userId);

    boolean existsByUserIdAndSubrubroId(Long userId, Long subrubroId);

    void deleteByUserId(Long userId);

    long countByUserId(Long userId);
}
