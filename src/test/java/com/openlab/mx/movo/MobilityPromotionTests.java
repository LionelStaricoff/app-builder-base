package com.openlab.mx.movo;

import com.openlab.mx.movo.mobility.promotion.entity.Discount;
import com.openlab.mx.movo.mobility.promotion.entity.DiscountStatus;
import com.openlab.mx.movo.mobility.promotion.entity.DiscountType;
import com.openlab.mx.movo.mobility.promotion.entity.Promotion;
import com.openlab.mx.movo.mobility.promotion.entity.PromotionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MobilityPromotionTests {

    @Test
    void promocionActivaDentroDeVentana() {
        LocalDateTime now = LocalDateTime.now();
        Promotion p = Promotion.builder().name("P").startsAt(now.minusDays(1))
                .expiresAt(now.plusDays(1)).commissionRate(new BigDecimal("0.05"))
                .targetPassengerCount(10).passengerCountingRule("pasajeros únicos con viaje finalizado")
                .status(PromotionStatus.ACTIVE).build();
        assertTrue(p.isActiveNow(now));
    }

    @Test
    void promocionExpiradaNoEstaActiva() {
        LocalDateTime now = LocalDateTime.now();
        Promotion p = Promotion.builder().name("P").startsAt(now.minusDays(2))
                .expiresAt(now.minusDays(1)).commissionRate(new BigDecimal("0.05"))
                .targetPassengerCount(10).passengerCountingRule("pasajeros únicos con viaje finalizado")
                .status(PromotionStatus.ACTIVE).build();
        assertFalse(p.isActiveNow(now));
    }

    @Test
    void descuentoRespetaLimitesYExpiracion() {
        LocalDateTime now = LocalDateTime.now();
        Discount d = Discount.builder().name("D").type(DiscountType.PERCENT).value(new BigDecimal("10"))
                .startsAt(now.minusDays(1)).expiresAt(now.plusDays(1)).maxUsage(2).usageCount(2)
                .status(DiscountStatus.ACTIVE).build();
        assertFalse(d.usable(now));
        d.setUsageCount(1);
        assertTrue(d.usable(now));
        d.setExpiresAt(now.minusDays(1));
        assertFalse(d.usable(now));
    }
}