package com.openlab.mx.movo.mobility.verification.repository;

import com.openlab.mx.movo.mobility.verification.entity.VehicleVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleVerificationRepository extends JpaRepository<VehicleVerification, Long> {
    Optional<VehicleVerification> findByUserId(Long userId);
}