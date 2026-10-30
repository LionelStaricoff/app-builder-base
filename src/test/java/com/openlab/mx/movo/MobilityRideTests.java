package com.openlab.mx.movo;

import com.openlab.mx.movo.mobility.ride.entity.Ride;
import com.openlab.mx.movo.mobility.ride.entity.RideOffer;
import com.openlab.mx.movo.mobility.ride.entity.RideOfferStatus;
import com.openlab.mx.movo.mobility.ride.entity.RideStatus;
import com.openlab.mx.movo.mobility.ride.service.RideService;
import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import com.openlab.mx.movo.mobility.rule.repository.CountryRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MobilityRideTests {

    @Autowired RideService rides;
    @Autowired CountryRuleRepository countries;

    private void ensureCountry() {
        if (countries.findByCountryCodeIgnoreCase("MX").isEmpty()) {
            countries.save(CountryRule.builder().countryCode("MX").countryName("México")
                    .currencyCode("MXN").minFare(new BigDecimal("50.00"))
                    .requiresPersonalIdentity(true).requiresVehicle(true).build());
        }
    }

    @Test
    void propuestaMenorAlMinimoSeRechaza() {
        ensureCountry();
        assertThrows(IllegalArgumentException.class, () ->
                rides.createRide(1L, 19.0, -99.0, 20.0, -100.0, "O", "D", "MX", null,
                        new BigDecimal("10.00")));
    }

    @Test
    void soloUnaOfertaGanaLaAceptacion() {
        ensureCountry();
        Ride ride = rides.createRide(1L, 19.0, -99.0, 20.0, -100.0, "O", "D", "MX", null,
                new BigDecimal("60.00"));
        RideOffer o1 = rides.counterOffer(ride.getId(), 2L, new BigDecimal("65.00"), 120);
        RideOffer o2 = rides.counterOffer(ride.getId(), 3L, new BigDecimal("70.00"), 120);
        rides.acceptOffer(o1.getId());
        assertEquals(RideStatus.ASSIGNED, rides.get(ride.getId()).getStatus());
        assertEquals(RideOfferStatus.ACCEPTED, rides.listOffers(ride.getId()).stream()
                .filter(o -> o.getId().equals(o1.getId())).findFirst().get().getStatus());
        // segunda aceptación falla
        assertThrows(IllegalStateException.class, () -> rides.acceptOffer(o2.getId()));
    }

    @Test
    void noPermiteSaltoDeEstadosInvalido() {
        ensureCountry();
        Ride ride = rides.createRide(1L, 19.0, -99.0, 20.0, -100.0, "O", "D", "MX", null,
                new BigDecimal("60.00"));
        RideOffer o = rides.counterOffer(ride.getId(), 2L, new BigDecimal("65.00"), 120);
        rides.acceptOffer(o.getId());
        assertThrows(IllegalStateException.class, () -> rides.advance(ride.getId(), RideStatus.FINISHED));
        rides.advance(ride.getId(), RideStatus.ARRIVING);
        rides.advance(ride.getId(), RideStatus.IN_PROGRESS);
        rides.advance(ride.getId(), RideStatus.FINISHED);
        assertTrue(rides.get(ride.getId()).getStatus() == RideStatus.FINISHED);
    }
}