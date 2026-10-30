package com.openlab.mx.movo.mobility.ride.service;

import com.openlab.mx.movo.mobility.ride.entity.Ride;
import com.openlab.mx.movo.mobility.ride.entity.RideOffer;
import com.openlab.mx.movo.mobility.ride.entity.RideOfferStatus;
import com.openlab.mx.movo.mobility.ride.entity.RideStatus;
import com.openlab.mx.movo.mobility.ride.repository.RideOfferRepository;
import com.openlab.mx.movo.mobility.ride.repository.RideRepository;
import com.openlab.mx.movo.mobility.rule.service.EffectiveRule;
import com.openlab.mx.movo.mobility.rule.service.RuleResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RideService {

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final RuleResolver ruleResolver;

    public RideService(RideRepository rides, RideOfferRepository offers, RuleResolver ruleResolver) {
        this.rides = rides;
        this.offers = offers;
        this.ruleResolver = ruleResolver;
    }

    @Transactional
    public Ride createRide(Long riderId, Double originLat, Double originLng, Double destinationLat,
                          Double destinationLng, String originText, String destinationText,
                          String countryCode, String cityName, BigDecimal proposedFare) {
        EffectiveRule rule = ruleResolver.resolve(countryCode, cityName);
        if (proposedFare.compareTo(rule.minFare()) < 0) {
            throw new IllegalArgumentException("La propuesta es menor al mínimo " + rule.minFare());
        }
        Ride ride = Ride.builder()
                .riderId(riderId)
                .originLat(originLat)
                .originLng(originLng)
                .destinationLat(destinationLat)
                .destinationLng(destinationLng)
                .originText(originText)
                .destinationText(destinationText)
                .countryCode(rule.countryCode())
                .cityName(cityName)
                .currencyCode(rule.currencyCode())
                .proposedFare(proposedFare)
                .status(RideStatus.SEARCHING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        return rides.save(ride);
    }

    @Transactional
    public RideOffer counterOffer(Long rideId, Long driverId, BigDecimal amount, int validitySeconds) {
        Ride ride = rides.findById(rideId).orElseThrow();
        if (ride.getStatus() != RideStatus.SEARCHING && ride.getStatus() != RideStatus.OFFERED) {
            throw new IllegalStateException("El viaje no acepta ofertas en estado " + ride.getStatus());
        }
        EffectiveRule rule = ruleResolver.resolve(ride.getCountryCode(), ride.getCityName());
        if (amount.compareTo(rule.minFare()) < 0) {
            throw new IllegalArgumentException("La contraoferta es menor al mínimo " + rule.minFare());
        }
        RideOffer offer = RideOffer.builder()
                .rideId(rideId)
                .driverId(driverId)
                .amount(amount)
                .currencyCode(rule.currencyCode())
                .status(RideOfferStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusSeconds(validitySeconds))
                .build();
        ride.setStatus(RideStatus.OFFERED);
        rides.save(ride);
        return offers.save(offer);
    }

    @Transactional
    public void acceptOffer(Long offerId) {
        RideOffer offer = offers.findById(offerId).orElseThrow();
        Ride ride = rides.findByIdForUpdate(offer.getRideId()).orElseThrow();
        if (ride.getStatus() != RideStatus.SEARCHING && ride.getStatus() != RideStatus.OFFERED) {
            throw new IllegalStateException("Viaje no aceptable en estado " + ride.getStatus());
        }
        if (offer.getStatus() != RideOfferStatus.PENDING) {
            throw new IllegalStateException("La oferta ya no está vigente");
        }
        if (LocalDateTime.now().isAfter(offer.getExpiresAt())) {
            throw new IllegalStateException("La oferta expiró");
        }
        // Garantiza una sola aceptación por viaje.
        if (offers.countByRideIdAndStatus(ride.getId(), RideOfferStatus.ACCEPTED) > 0) {
            throw new IllegalStateException("Ya existe una oferta aceptada");
        }
        offer.setStatus(RideOfferStatus.ACCEPTED);
        offer.setRespondedAt(LocalDateTime.now());
        offers.save(offer);

        // Rechazar otras ofertas pendientes.
        List<RideOffer> others = offers.findByRideIdOrderByCreatedAtAsc(ride.getId());
        for (RideOffer other : others) {
            if (!other.getId().equals(offer.getId()) && other.getStatus() == RideOfferStatus.PENDING) {
                other.setStatus(RideOfferStatus.REJECTED);
                other.setRespondedAt(LocalDateTime.now());
                offers.save(other);
            }
        }

        ride.setDriverId(offer.getDriverId());
        ride.setStatus(RideStatus.ASSIGNED);
        rides.save(ride);
    }

    @Transactional
    public Ride advance(Long rideId, RideStatus next) {
        Ride ride = rides.findByIdForUpdate(rideId).orElseThrow();
        RideStatus current = ride.getStatus();
        boolean valid = switch (current) {
            case ASSIGNED -> next == RideStatus.ARRIVING || next == RideStatus.CANCELLED;
            case ARRIVING -> next == RideStatus.IN_PROGRESS || next == RideStatus.CANCELLED;
            case IN_PROGRESS -> next == RideStatus.FINISHED || next == RideStatus.CANCELLED;
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Transición inválida " + current + " -> " + next);
        }
        ride.setStatus(next);
        return rides.save(ride);
    }

    @Transactional
    public Ride cancel(Long rideId) {
        Ride ride = rides.findByIdForUpdate(rideId).orElseThrow();
        if (ride.getStatus() == RideStatus.FINISHED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cancelar en estado " + ride.getStatus());
        }
        ride.setStatus(RideStatus.CANCELLED);
        return rides.save(ride);
    }

    public Ride get(Long rideId) {
        return rides.findById(rideId).orElseThrow();
    }

    public List<RideOffer> listOffers(Long rideId) {
        return offers.findByRideIdOrderByCreatedAtAsc(rideId);
    }
}