package com.openlab.mx.movo.mobility.rule.service;

import java.math.BigDecimal;

/**
 * Regla efectiva ya resuelta (país + override de ciudad). Ningún campo es null:
 * todo override null ya fue heredado del país.
 */
public record EffectiveRule(
        String countryCode,
        String cityName,
        String currencyCode,
        BigDecimal minFare,
        boolean requiresPersonalIdentity,
        boolean requiresVehicle
) {}