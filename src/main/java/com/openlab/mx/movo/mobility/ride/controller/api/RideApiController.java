package com.openlab.mx.movo.mobility.ride.controller.api;

import com.openlab.mx.movo.mobility.ride.entity.Ride;
import com.openlab.mx.movo.mobility.ride.entity.RideOffer;
import com.openlab.mx.movo.mobility.ride.entity.RideOfferStatus;
import com.openlab.mx.movo.mobility.ride.entity.RideStatus;
import com.openlab.mx.movo.mobility.ride.service.RideService;
import com.openlab.mx.movo.payment.service.RidePaymentService;
import com.openlab.mx.movo.shared.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideApiController {

    private final RideService rideService;
    private final RidePaymentService ridePaymentService;
    private final CurrentUser currentUser;

    public record CreateRideRequest(
            @NotNull Double originLat,
            @NotNull Double originLng,
            Double destinationLat,
            Double destinationLng,
            String originText,
            String destinationText,
            @NotNull String countryCode,
            String cityName,
            @NotNull @DecimalMin("0.01") BigDecimal proposedFare) {
    }

    public record CounterOfferRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Positive int validitySeconds) {
    }

    public record AdvanceRequest(@NotNull RideStatus next) {
    }

    public record RideResponse(
            Long id,
            Long riderId,
            Long driverId,
            Double originLat,
            Double originLng,
            Double destinationLat,
            Double destinationLng,
            String originText,
            String destinationText,
            String countryCode,
            String cityName,
            String currencyCode,
            BigDecimal proposedFare,
            RideStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime expiresAt) {
    }

    public record RideOfferResponse(
            Long id,
            Long rideId,
            Long driverId,
            BigDecimal amount,
            String currencyCode,
            RideOfferStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            LocalDateTime respondedAt) {
    }

    private RideResponse toRideResponse(Ride r) {
        return new RideResponse(
                r.getId(), r.getRiderId(), r.getDriverId(), r.getOriginLat(), r.getOriginLng(),
                r.getDestinationLat(), r.getDestinationLng(), r.getOriginText(), r.getDestinationText(),
                r.getCountryCode(), r.getCityName(), r.getCurrencyCode(), r.getProposedFare(),
                r.getStatus(), r.getCreatedAt(), r.getUpdatedAt(), r.getExpiresAt());
    }

    private RideOfferResponse toOfferResponse(RideOffer o) {
        return new RideOfferResponse(
                o.getId(), o.getRideId(), o.getDriverId(), o.getAmount(), o.getCurrencyCode(),
                o.getStatus(), o.getCreatedAt(), o.getExpiresAt(), o.getRespondedAt());
    }

    @PostMapping
    public ResponseEntity<RideResponse> create(@Valid @RequestBody CreateRideRequest req) {
        Long riderId = currentUser.require().getId();
        Ride ride = rideService.createRide(
                riderId, req.originLat(), req.originLng(), req.destinationLat(), req.destinationLng(),
                req.originText(), req.destinationText(), req.countryCode(), req.cityName(), req.proposedFare());
        return ResponseEntity.ok(toRideResponse(ride));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideResponse> get(@PathVariable Long id) {
        Ride ride = rideService.get(id);
        return ResponseEntity.ok(toRideResponse(ride));
    }

    @PostMapping("/{id}/offers")
    public ResponseEntity<RideOfferResponse> counterOffer(@PathVariable Long id,
                                                         @Valid @RequestBody CounterOfferRequest req) {
        Long driverId = currentUser.require().getId();
        RideOffer offer = rideService.counterOffer(id, driverId, req.amount(), req.validitySeconds());
        return ResponseEntity.ok(toOfferResponse(offer));
    }

    @GetMapping("/{id}/offers")
    public ResponseEntity<List<RideOfferResponse>> listOffers(@PathVariable Long id) {
        List<RideOfferResponse> body = rideService.listOffers(id).stream()
                .map(this::toOfferResponse).toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/offers/{offerId}/accept")
    public ResponseEntity<Void> acceptOffer(@PathVariable Long offerId) {
        rideService.acceptOffer(offerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/advance")
    public ResponseEntity<RideResponse> advance(@PathVariable Long id,
                                               @Valid @RequestBody AdvanceRequest req) {
        Ride ride = rideService.advance(id, req.next());
        if (req.next() == RideStatus.FINISHED) {
            ridePaymentService.captureOnFinish(id);
        }
        return ResponseEntity.ok(toRideResponse(ride));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<RideResponse> cancel(@PathVariable Long id) {
        Ride ride = rideService.cancel(id);
        ridePaymentService.cancel(id);
        return ResponseEntity.ok(toRideResponse(ride));
    }
}
