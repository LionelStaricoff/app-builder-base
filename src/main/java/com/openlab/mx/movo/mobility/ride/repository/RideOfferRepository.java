package com.openlab.mx.movo.mobility.ride.repository;

import com.openlab.mx.movo.mobility.ride.entity.RideOffer;
import com.openlab.mx.movo.mobility.ride.entity.RideOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideOfferRepository extends JpaRepository<RideOffer, Long> {
    List<RideOffer> findByRideIdOrderByCreatedAtAsc(Long rideId);
    Optional<RideOffer> findByIdAndRideId(Long id, Long rideId);
    Optional<RideOffer> findByRideIdAndStatus(Long rideId, RideOfferStatus status);
    long countByRideIdAndStatus(Long rideId, RideOfferStatus status);
}