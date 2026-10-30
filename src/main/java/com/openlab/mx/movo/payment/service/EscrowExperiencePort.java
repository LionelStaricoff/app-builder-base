package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.user.entity.User;

import java.util.Optional;

/**
 * Port de explotacion de datos de experiencia para el escrow (Corte C T8).
 * Permite que payment no importe entidades de marketplace; la feature
 * experience implementa este port.
 */
public interface EscrowExperiencePort {
    boolean isInstanceCompleted(Long instanceId);

    /** Todos los participantes confirmados ya evaluaron. */
    boolean allConfirmedRated(Long instanceId);

    java.util.Optional<User> experienceCreator(Long instanceId);

    String experienceName(Long instanceId);
}
