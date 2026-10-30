package com.openlab.mx.movo;

import com.openlab.mx.movo.mobility.rule.entity.CityRuleOverride;
import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import com.openlab.mx.movo.mobility.rule.repository.CityRuleOverrideRepository;
import com.openlab.mx.movo.mobility.rule.repository.CountryRuleRepository;
import com.openlab.mx.movo.mobility.rule.service.EffectiveRule;
import com.openlab.mx.movo.mobility.rule.service.MissingCountryRuleException;
import com.openlab.mx.movo.mobility.rule.service.RuleResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MobilityGeoRuleTests {

    @Autowired
    RuleResolver resolver;
    @Autowired
    CountryRuleRepository countryRules;
    @Autowired
    CityRuleOverrideRepository cityOverrides;

    @Test
    void heredaDelPaisCuandoNoHayOverrideDeCiudad() {
        CountryRule mx = countryRules.save(CountryRule.builder()
                .countryCode("MX").countryName("México").currencyCode("MXN")
                .minFare(new BigDecimal("50.00"))
                .requiresPersonalIdentity(true).requiresVehicle(true).build());
        EffectiveRule effective = resolver.resolve("mx", "Guadalajara");
        assertEquals("MX", effective.countryCode());
        assertEquals("MXN", effective.currencyCode());
        assertEquals(new BigDecimal("50.00"), effective.minFare());
        assertTrue(effective.requiresVehicle());
        assertTrue(effective.requiresPersonalIdentity());
        countryRules.delete(mx);
    }

    @Test
    void overrideDeCiudadSustituyeSoloCamposConfigurados() {
        CountryRule mx = countryRules.save(CountryRule.builder()
                .countryCode("MX2").countryName("México").currencyCode("MXN")
                .minFare(new BigDecimal("50.00"))
                .requiresPersonalIdentity(true).requiresVehicle(true).build());

        CityRuleOverride override = CityRuleOverride.builder()
                .countryRule(mx).cityName("CDMX")
                .currencyCode("MXN").minFare(new BigDecimal("70.00"))
                .requiresPersonalIdentity(null).requiresVehicle(null).build();
        cityOverrides.save(override);

        EffectiveRule effective = resolver.resolve("MX2", "CDMX");
        assertEquals(new BigDecimal("70.00"), effective.minFare());
        assertTrue(effective.requiresPersonalIdentity()); // heredado
        assertTrue(effective.requiresVehicle()); // heredado

        cityOverrides.delete(override);
        countryRules.delete(mx);
    }

    @Test
    void sinReglaDePaisBloquea() {
        assertThrows(MissingCountryRuleException.class, () -> resolver.resolve("ZZ", "Ciudad X"));
        org.junit.jupiter.api.Assertions.assertFalse(resolver.countryRuleExists("ZZ"));
    }
}