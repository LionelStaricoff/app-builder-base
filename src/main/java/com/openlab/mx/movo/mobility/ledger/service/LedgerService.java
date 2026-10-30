package com.openlab.mx.movo.mobility.ledger.service;

import com.openlab.mx.movo.mobility.ledger.entity.LedgerEntry;
import com.openlab.mx.movo.mobility.ledger.entity.LedgerEntryType;
import com.openlab.mx.movo.mobility.ledger.repository.LedgerEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class LedgerService {

    private final LedgerEntryRepository repo;
    private final BigDecimal debtLimit;

    public LedgerService(LedgerEntryRepository repo,
                         @Value("${movo.debt.limit.mxn:500}") BigDecimal debtLimit) {
        this.repo = repo;
        this.debtLimit = debtLimit;
    }

    @Transactional(readOnly = true)
    public WalletBalance balance(Long driverId, String currencyCode) {
        List<LedgerEntry> entries = repo.findByDriverIdAndCurrencyCodeOrderByCreatedAtAsc(driverId, currencyCode);
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal debt = BigDecimal.ZERO;
        for (LedgerEntry e : entries) {
            available = available.add(e.getAmount());
            debt = debt.add(e.getDebtDelta());
        }
        return new WalletBalance(currencyCode, scale(available), scale(debt));
    }

    @Transactional
    public LedgerEntry recordCashCommission(Long driverId, Long rideId, BigDecimal commission,
                                           String currencyCode, String idempotencyKey) {
        LedgerEntry existing = repo.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return existing;
        BigDecimal currentDebt = balance(driverId, currencyCode).pendingDebt();
        if (currentDebt.add(commission).compareTo(debtLimit) > 0) {
            throw new IllegalStateException("Límite de deuda excedido");
        }
        LedgerEntry entry = LedgerEntry.builder()
                .driverId(driverId)
                .rideId(rideId)
                .type(LedgerEntryType.CASH_COMMISSION_DEBT)
                .amount(BigDecimal.ZERO)
                .debtDelta(scale(commission))
                .currencyCode(currencyCode)
                .idempotencyKey(idempotencyKey)
                .description("Comisión viaje efectivo registrada como deuda")
                .build();
        return repo.save(entry);
    }

    @Transactional
    public LedgerEntry recordCardPayment(Long driverId, Long rideId, BigDecimal grossAmount,
                                        BigDecimal commissionRate, String currencyCode,
                                        String idempotencyKey) {
        LedgerEntry existing = repo.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return existing;
        BigDecimal commission = scale(grossAmount.multiply(commissionRate));
        BigDecimal gross = scale(grossAmount);
        BigDecimal currentDebt = balance(driverId, currencyCode).pendingDebt();
        BigDecimal payableDebt = currentDebt.min(gross.subtract(commission));
        if (payableDebt.compareTo(BigDecimal.ZERO) < 0) payableDebt = BigDecimal.ZERO;
        BigDecimal available = gross.subtract(commission).subtract(payableDebt);
        LedgerEntry entry = LedgerEntry.builder()
                .driverId(driverId)
                .rideId(rideId)
                .type(LedgerEntryType.CARD_PAYMENT)
                .amount(scale(available))
                .debtDelta(scale(payableDebt.negate()))
                .currencyCode(currencyCode)
                .idempotencyKey(idempotencyKey)
                .description("Comisión " + commission + " aplicada antes de disponible")
                .build();
        return repo.save(entry);
    }

    @Transactional
    public void requestWithdrawal(Long driverId, BigDecimal amount, String currencyCode, String idempotencyKey) {
        LedgerEntry existing = repo.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return;
        WalletBalance b = balance(driverId, currencyCode);
        BigDecimal requested = scale(amount);
        if (requested.compareTo(b.available()) > 0) {
            throw new IllegalStateException("Saldo disponible insuficiente");
        }
        LedgerEntry entry = LedgerEntry.builder()
                .driverId(driverId)
                .type(LedgerEntryType.WITHDRAWAL_REQUEST)
                .amount(requested.negate())
                .debtDelta(BigDecimal.ZERO)
                .currencyCode(currencyCode)
                .idempotencyKey(idempotencyKey)
                .description("Solicitud de retiro")
                .build();
        repo.save(entry);
    }

    public boolean canAcceptCashRide(Long driverId, String currencyCode, BigDecimal newDebt) {
        BigDecimal current = balance(driverId, currencyCode).pendingDebt();
        return current.add(newDebt).compareTo(debtLimit) <= 0;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}