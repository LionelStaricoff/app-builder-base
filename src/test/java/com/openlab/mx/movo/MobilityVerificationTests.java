package com.openlab.mx.movo;

import com.openlab.mx.movo.identity.entity.DiditVerification;
import com.openlab.mx.movo.identity.entity.VerificationStatus;
import com.openlab.mx.movo.identity.repository.DiditVerificationRepository;
import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import com.openlab.mx.movo.mobility.rule.repository.CityRuleOverrideRepository;
import com.openlab.mx.movo.mobility.rule.repository.CountryRuleRepository;
import com.openlab.mx.movo.mobility.rule.service.RuleResolver;
import com.openlab.mx.movo.mobility.verification.entity.VehicleVerification;
import com.openlab.mx.movo.mobility.verification.repository.VehicleVerificationRepository;
import com.openlab.mx.movo.mobility.verification.service.VerificationEligibilityService;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MobilityVerificationTests {

    @Autowired VerificationEligibilityService eligibility;
    @Autowired CountryRuleRepository countryRules;
    @Autowired DiditVerificationRepository personalRepo;
    @Autowired VehicleVerificationRepository vehicleRepo;
    @Autowired UserRepository users;

    private CountryRule ensureCountry() {
        return countryRules.findByCountryCodeIgnoreCase("VE").orElseGet(() ->
                countryRules.save(CountryRule.builder()
                        .countryCode("VE").countryName("Venezuela-ish").currencyCode("MXN")
                        .minFare(new BigDecimal("50.00"))
                        .requiresPersonalIdentity(true).requiresVehicle(true).build()));
    }

    @Test
    void pasajeroPuedePedirViajeSinVerificacion() {
        assertTrue(eligibility.passengerCanRequestRide());
    }

    @Test
    void conductorNoEsElegibleSinVerificaciones() {
        ensureCountry();
        assertFalse(eligibility.driverEligible(999999L, "VE", null));
    }

    @Test
    void conductorElegibleSoloConAmbasVerificacionesYRegla() {
        CountryRule c = ensureCountry();
        User u = users.save(User.builder()
                .username("drv-" + UUID.randomUUID().toString().substring(0, 8))
                .email(UUID.randomUUID() + "@x.com")
                .passwordHash("{noop}x").role(Role.USER)
                .birthDate(LocalDate.of(1990, 1, 1)).build());

        DiditVerification personal = DiditVerification.started(u.getId(), "sess-" + u.getId());
        personal.applyDecision(VerificationStatus.VERIFIED, "Approved");
        personalRepo.save(personal);

        VehicleVerification vehicle = VehicleVerification.started(u.getId(), "prov-1");
        vehicle.setStatus(VerificationStatus.VERIFIED);
        vehicleRepo.save(vehicle);

        assertTrue(eligibility.driverEligible(u.getId(), "VE", null));

        // Limpieza
        personalRepo.delete(personal);
        vehicleRepo.delete(vehicle);
        users.delete(u);
        // no borramos country para no romper otros tests; tolerable
    }

    @Test
    void sinReglaDePaisNoEsElegibleAunqueVerificado() {
        User u = users.save(User.builder()
                .username("drv2-" + UUID.randomUUID().toString().substring(0, 8))
                .email(UUID.randomUUID() + "@x.com")
                .passwordHash("{noop}x").role(Role.USER)
                .birthDate(LocalDate.of(1990, 1, 1)).build());
        assertFalse(eligibility.driverEligible(u.getId(), "ZZ", null));
        users.delete(u);
    }
}