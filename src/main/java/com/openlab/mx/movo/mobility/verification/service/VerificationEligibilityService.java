package com.openlab.mx.movo.mobility.verification.service;

import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import com.openlab.mx.movo.mobility.rule.service.EffectiveRule;
import com.openlab.mx.movo.mobility.rule.service.RuleResolver;
import com.openlab.mx.movo.mobility.verification.entity.VehicleVerification;
import com.openlab.mx.movo.mobility.verification.repository.VehicleVerificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Política de verificación:
 * - Pasajero: verificación personal opcional, nunca bloquea pedir viaje.
 * - Conductor: identidad personal obligatoria y verificación vehicular obligatoria,
 *   según la regla efectiva de su ubicación. Sin regla de país no es elegible.
 */
@Service
public class VerificationEligibilityService {

    private final DiditVerificationRepository personalVerifications;
    private final VehicleVerificationRepository vehicleVerifications;
    private final RuleResolver ruleResolver;

    public VerificationEligibilityService(DiditVerificationRepository personalVerifications,
                                         VehicleVerificationRepository vehicleVerifications,
                                         RuleResolver ruleResolver) {
        this.personalVerifications = personalVerifications;
        this.vehicleVerifications = vehicleVerifications;
        this.ruleResolver = ruleResolver;
    }

    @Transactional(readOnly = true)
    public boolean isPersonalVerified(Long userId) {
        return userId != null && personalVerifications.findByUserId(userId)
                .map(DiditVerification::isVerified)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isVehicleVerified(Long userId) {
        return userId != null && vehicleVerifications.findByUserId(userId)
                .map(VehicleVerification::isVerified)
                .orElse(false);
    }

    public boolean driverEligible(Long userId, String countryCode, String cityName) {
        if (!ruleResolver.countryRuleExists(countryCode)) {
            return false;
        }
        EffectiveRule rule = ruleResolver.resolve(countryCode, cityName);
        if (rule.requiresPersonalIdentity() && !isPersonalVerified(userId)) {
            return false;
        }
        if (rule.requiresVehicle() && !isVehicleVerified(userId)) {
            return false;
        }
        return true;
    }

    public boolean passengerCanRequestRide() {
        return true;
    }
}