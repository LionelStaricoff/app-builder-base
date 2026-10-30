package com.openlab.mx.movo;

import com.openlab.mx.movo.mobility.ledger.entity.LedgerEntry;
import com.openlab.mx.movo.mobility.ledger.service.LedgerService;
import com.openlab.mx.movo.mobility.ledger.service.WalletBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MobilityLedgerTests {

    @Autowired LedgerService ledger;

    @Test
    void comisionEnEfectivoSeRegistraComoDeuda() {
        long driver = 1001;
        ledger.recordCashCommission(driver, 1L, new BigDecimal("50.00"), "MXN", "cash-1");
        WalletBalance b = ledger.balance(driver, "MXN");
        assertEquals(new BigDecimal("0.00"), b.available());
        assertEquals(new BigDecimal("50.00"), b.pendingDebt());
    }

    @Test
    void webhookDuplicadoNoDuplicaDeuda() {
        long driver = 1002;
        ledger.recordCashCommission(driver, 2L, new BigDecimal("40.00"), "MXN", "dup-key");
        ledger.recordCashCommission(driver, 2L, new BigDecimal("40.00"), "MXN", "dup-key");
        WalletBalance b = ledger.balance(driver, "MXN");
        assertEquals(new BigDecimal("40.00"), b.pendingDebt());
    }

    @Test
    void pagoConTarjetaAplicaComisionYDeudaAntesDeDisponible() {
        long driver = 1003;
        ledger.recordCashCommission(driver, 3L, new BigDecimal("30.00"), "MXN", "cash-1003");
        ledger.recordCardPayment(driver, 4L, new BigDecimal("100.00"), new BigDecimal("0.10"),
                "MXN", "card-1003");
        WalletBalance b = ledger.balance(driver, "MXN");
        // comision 10, deuda 30 => disponible 60
        assertEquals(new BigDecimal("60.00"), b.available());
        assertEquals(new BigDecimal("0.00"), b.pendingDebt());
    }

    @Test
    void respetaLimiteDeDeuda() {
        long driver = 1004;
        ledger.recordCashCommission(driver, 5L, new BigDecimal("500.00"), "MXN", "cash-limit");
        assertFalse(ledger.canAcceptCashRide(driver, "MXN", new BigDecimal("10.00")));
    }

    @Test
    void saldosSonIndependientesPorMoneda() {
        long driver = 1005;
        ledger.recordCashCommission(driver, 6L, new BigDecimal("10.00"), "USD", "cash-usd");
        WalletBalance mxn = ledger.balance(driver, "MXN");
        WalletBalance usd = ledger.balance(driver, "USD");
        assertEquals(new BigDecimal("0.00"), mxn.pendingDebt());
        assertEquals(new BigDecimal("10.00"), usd.pendingDebt());
    }

    @Test
    void redondeoDecimal() {
        long driver = 1006;
        ledger.recordCardPayment(driver, 7L, new BigDecimal("100.00"), new BigDecimal("0.333333"),
                "MXN", "round-key");
        WalletBalance b = ledger.balance(driver, "MXN");
        assertEquals(new BigDecimal("66.67"), b.available());
    }
}