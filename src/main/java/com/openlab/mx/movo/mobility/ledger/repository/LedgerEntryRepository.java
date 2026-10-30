package com.openlab.mx.movo.mobility.ledger.repository;

import com.openlab.mx.movo.mobility.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Optional<LedgerEntry> findByIdempotencyKey(String key);
    List<LedgerEntry> findByDriverIdOrderByCreatedAtAsc(Long driverId);
    List<LedgerEntry> findByDriverIdAndCurrencyCodeOrderByCreatedAtAsc(Long driverId, String currencyCode);
}