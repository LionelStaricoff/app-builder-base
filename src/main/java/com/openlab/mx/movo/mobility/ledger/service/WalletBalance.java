package com.openlab.mx.movo.mobility.ledger.service;

import java.math.BigDecimal;

public record WalletBalance(String currencyCode, BigDecimal available, BigDecimal pendingDebt) {}