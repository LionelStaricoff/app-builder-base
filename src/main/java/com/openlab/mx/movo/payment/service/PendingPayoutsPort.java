package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.payment.entity.Payment;

import java.util.List;

/**
 * Port para obtener los pagos pendientes de payout de un creador sin que
 * payment importe Reservation/Experience directamente (Corte E T8).
 */
public interface PendingPayoutsPort {
    List<Payment> pendingPaymentsOfCreator(Long creatorId);
}