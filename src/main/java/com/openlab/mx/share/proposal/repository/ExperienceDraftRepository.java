package com.openlab.mx.share.proposal.repository;

import com.openlab.mx.share.proposal.entity.ExperienceDraft;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExperienceDraftRepository extends JpaRepository<ExperienceDraft, Long> {

    /** El borrador más reciente del usuario: es el que precarga la pantalla 3. */
    Optional<ExperienceDraft> findTopByUserIdOrderByUpdatedAtDesc(Long userId);

    List<ExperienceDraft> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
