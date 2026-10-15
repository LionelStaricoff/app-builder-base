package com.openlab.mx.share.identity.repository;

import com.openlab.mx.share.identity.entity.DiditVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiditVerificationRepository extends JpaRepository<DiditVerification, Long> {

    Optional<DiditVerification> findByUserId(Long userId);

    Optional<DiditVerification> findByDiditSessionId(String diditSessionId);
}
