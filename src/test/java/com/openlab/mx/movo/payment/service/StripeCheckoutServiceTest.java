package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.notification.service.NotificationService;
import com.openlab.mx.movo.payment.entity.Payment;
import com.openlab.mx.movo.payment.entity.PaymentStatus;
import com.openlab.mx.movo.payment.gateway.FakePaymentGateway;
import com.openlab.mx.movo.payment.gateway.GatewayCheckoutRequest;
import com.openlab.mx.movo.payment.gateway.GatewaySession;
import com.openlab.mx.movo.payment.repository.PaymentRepository;
import com.openlab.mx.movo.reservation.entity.Reservation;
import com.openlab.mx.movo.reservation.entity.ReservationStatus;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.reservation.service.ReservationService;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.shared.exception.NotOwnerException;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec §5-6: reserva PENDIENTE + Payment PENDING → sesión Stripe →
 * PENDING→HELD idempotente (retorno o webhook), expire libera el cupo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripeCheckoutServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock NotificationService notificationService;
    @Mock com.openlab.mx.movo.payment.service.ReservationConfirmationPort confirmationPort;
    @Mock com.openlab.mx.movo.payment.service.CheckoutReservationPort checkoutReservationPort;

    FakePaymentGateway gateway;
    StripeCheckoutService service;

    // Los mocks se crean antes de cada test: construir el servicio ahí, no en
    // el inicializador de campos (capturaría los mocks como null).
    @BeforeEach
    void setUp() {
        gateway = new FakePaymentGateway();
        service = new StripeCheckoutService(
                paymentRepository, notificationService,
                confirmationPort, checkoutReservationPort, gateway);
    }

    // ---------- fixtures ----------

    private User usuario(long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private ExperienceInstance instancia() {
        Experience exp = Experience.builder()
                .id(7L).name("Taller de cerámica")
                .price(new BigDecimal("380.00"))
                .currencyCode("MXN")
                .maxCapacity(50)
                .build();
        return ExperienceInstance.builder().id(1L).experience(exp).build();
    }

    private Reservation reserva(long id, ExperienceInstance instance, User u, int attendees) {
        return Reservation.builder()
                .id(id).instance(instance).user(u).attendees(attendees)
                .status(ReservationStatus.PENDIENTE).createdAt(java.time.LocalDateTime.now())
                .build();
    }

    private Payment pendiente(Reservation r) {
        return Payment.builder()
                .id(5L).reservationId(r.getId())
                .amount(new BigDecimal("380.00")).commission(new BigDecimal("19.00"))
                .status(PaymentStatus.PENDING).stripeSessionId("cs_fake_1")
                .build();
    }

    // ---------- casos ----------

    @Test
    void startGuardaPaymentPendingConMontoYComisionDesdeElDominio() {
        User u = usuario(10L);
        ExperienceInstance instance = instancia();
        Reservation r = reserva(99L, instance, u, 2);
        when(checkoutReservationPort.findPendingReservation(1L, 10L)).thenReturn(Optional.empty());
        CheckoutReservationPort.ReservationCheckoutData rdata = new CheckoutReservationPort.ReservationCheckoutData(99L, 2, 7L, new BigDecimal("380.00"), "MXN", "Taller de cerámica");
        when(checkoutReservationPort.reserveReservation(1L, 2, 10L)).thenReturn(rdata);
        when(paymentRepository.findByReservationId(99L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewaySession gs = service.start(1L, 2, u);

        assertEquals("cs_fake_1", gs.sessionId());
        assertTrue(gs.url().contains("/__fake_stripe_/checkout/cs_fake_1"));

        org.mockito.ArgumentCaptor<Payment> captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("760.00")),
                "monto = price × attendees (760), calculado en el back (spec §11.1)");
        assertEquals(0, saved.getCommission().compareTo(new BigDecimal("38.00")),
                "comisión = 5% del monto (38)");
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals("cs_fake_1", saved.getStripeSessionId());
        assertNull(saved.getHeldAt(), "heldAt solo se setea al pasar a HELD (spec §3.2)");

        GatewayCheckoutRequest pedido = gateway.lastRequest();
        assertEquals("MXN", pedido.currency());
        assertEquals(0, pedido.unitAmount().compareTo(new BigDecimal("380.00")), "unitAmount = precio unitario");
        assertEquals(2, pedido.quantity());
        assertEquals(String.valueOf(1L), pedido.metadata().get("instanceId"));
        assertEquals("10", pedido.metadata().get("userId"));
        assertEquals("2", pedido.metadata().get("attendees"));
    }

    @Test
    void startSinCurrencyCodeUsaMXN() {
        User u = usuario(10L);
        ExperienceInstance instance = instancia();
        instance.getExperience().setCurrencyCode(null);
        Reservation r = reserva(99L, instance, u, 1);
        when(checkoutReservationPort.findPendingReservation(1L, 10L)).thenReturn(Optional.empty());
        CheckoutReservationPort.ReservationCheckoutData rdata = new CheckoutReservationPort.ReservationCheckoutData(99L, 1, 7L, new BigDecimal("380.00"), null, "Taller de cerámica");
        when(checkoutReservationPort.reserveReservation(1L, 1, 10L)).thenReturn(rdata);
        when(paymentRepository.findByReservationId(99L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.start(1L, 1, u);
        assertEquals("MXN", gateway.lastRequest().currency(), "default MXN de spec §11.1");
    }

    @Test
    void dobleSubmitReusaLaMismaSesion() {
        User u = usuario(10L);
        Reservation r = reserva(99L, instancia(), u, 2);
        Payment pago = pendiente(r);
        gateway.createCheckout(new GatewayCheckoutRequest(new BigDecimal("380.00"),
                "MXN", 2, "Taller de cerámica", java.util.Map.of())); // sesión cs_fake_1 existe en el fake
        CheckoutReservationPort.ReservationCheckoutData ddata = new CheckoutReservationPort.ReservationCheckoutData(99L, 2, 7L, new BigDecimal("380.00"), "MXN", "Taller de cerámica");
        when(checkoutReservationPort.findPendingReservation(1L, 10L)).thenReturn(Optional.of(ddata));
        when(paymentRepository.findByReservationId(99L)).thenReturn(Optional.of(pago));

        GatewaySession gs = service.start(1L, 2, u);

        assertEquals("cs_fake_1", gs.sessionId(), "reusa la sesión del POST anterior (spec §11.2)");
        verify(checkoutReservationPort, never()).reserveReservation(any(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void completePoneHeldConReferenciaPi() {
        User u = usuario(10L);
        Reservation r = reserva(99L, instancia(), u, 2);
        Payment pago = pendiente(r);
        gateway.createCheckout(new GatewayCheckoutRequest(new BigDecimal("380.00"),
                "MXN", 2, "Taller de cerámica", java.util.Map.of())); // cs_fake_1/pi_fake_1 reales en el fake
        when(paymentRepository.findFirstByStripeSessionId("cs_fake_1"))
                .thenReturn(Optional.of(pago));
        when(checkoutReservationPort.reservationOwnerId(99L)).thenReturn(Optional.of(10L));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment held = service.complete("cs_fake_1", 10L);

        assertEquals(PaymentStatus.HELD, held.getStatus());
        assertEquals("pi_fake_1", held.getStripePaymentIntentId());
        assertNotNull(held.getHeldAt());
    }

    @Test
    void completeEsIdempotente() {
        Reservation r = reserva(99L, instancia(), usuario(10L), 2);
        Payment pago = pendiente(r);
        pago.setStatus(PaymentStatus.HELD);
        pago.setHeldAt(java.time.LocalDateTime.now().minusMinutes(5));
        when(paymentRepository.findFirstByStripeSessionId("cs_fake_1"))
                .thenReturn(Optional.of(pago));

        service.complete("cs_fake_1", 10L);

        // ya HELD: no vuelve a persistir, heldAt intacto (idempotencia, spec §5)
        verify(paymentRepository, never()).save(any(Payment.class));
        assertNotNull(pago.getHeldAt());
    }

    @Test
    void completeDeUsuarioAjenoNoCambiaEstado() {
        Reservation r = reserva(99L, instancia(), usuario(10L), 2);
        Payment pago = pendiente(r);
        when(paymentRepository.findFirstByStripeSessionId("cs_fake_1"))
                .thenReturn(Optional.of(pago));

        assertThrows(NotOwnerException.class, () -> service.complete("cs_fake_1", 20L));
        assertEquals(PaymentStatus.PENDING, pago.getStatus());
    }

    @Test
    void completeDeSesionInexistenteEsDomainException() {
        assertThrows(DomainException.class, () -> service.complete("cs_fantasma", 10L));
    }

    @Test
    void expireDePendingFallaElPagoYLiberaElCupo() {
        User u = usuario(10L);
        Reservation r = reserva(99L, instancia(), u, 2);
        Payment pago = pendiente(r);
        when(paymentRepository.findFirstByStripeSessionId("cs_fake_1"))
                .thenReturn(Optional.of(pago));
        when(checkoutReservationPort.cancelPendingReservation(99L)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.expire("cs_fake_1");

        assertEquals(PaymentStatus.FAILED, pago.getStatus());
        assertTrue(checkoutReservationPort.cancelPendingReservation(99L));
    }

    @Test
    void expireDePagoHeldONoConocidoEsNoOp() {
        Reservation r = reserva(99L, instancia(), usuario(10L), 2);
        Payment held = pendiente(r);
        held.setStatus(PaymentStatus.HELD);
        when(paymentRepository.findFirstByStripeSessionId("cs_fake_1"))
                .thenReturn(Optional.of(held))
                .thenReturn(Optional.empty());

        service.expire("cs_fake_1");
        assertEquals(PaymentStatus.HELD, held.getStatus());

        service.expire("cs_desconocida");
        verify(checkoutReservationPort, never()).cancelPendingReservation(any());
    }
}
