package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.payment.entity.ConnectAccount;
import com.openlab.mx.movo.payment.entity.ConnectAccountStatus;
import com.openlab.mx.movo.payment.gateway.PaymentGateway;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.shared.exception.UnsupportedPayoutCountryException;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El enlace de onboarding es lo que manda al creador a Stripe. Si la pasarela
 * devuelve un link vacío, el controller reventaba con NullPointerException
 * (500 sin mensaje). Ahora el servicio lo corta con un error de dominio.
 */
class StripeConnectServiceTest {

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final ConnectAccountService connectAccountService = mock(ConnectAccountService.class);
    private final StripeConnectService service = new StripeConnectService(connectAccountService, gateway);

    private final User creator = User.builder()
            .id(1L)
            .username("demo_creator")
            .email("creador@example.com")
            .passwordHash("hash")
            .birthDate(LocalDate.of(1990, 1, 1))
            .role(Role.CREATOR)
            .build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "payoutCountriesConfig", "MX,US");
    }

    @Test
    void devuelveElLinkCuandoLaPasarelaResponde() {
        when(connectAccountService.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString()))
                .thenReturn("https://connect.stripe.com/setup/e/abc");

        assertEquals("https://connect.stripe.com/setup/e/abc",
                service.generateOnboardingLink(creator, "MX"));
    }

    @Test
    void unLinkNullCortaConUnErrorDeDominioYNoConNullPointer() {
        when(connectAccountService.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString())).thenReturn(null);

        DomainException e = assertThrows(DomainException.class,
                () -> service.generateOnboardingLink(creator, "MX"));
        assertTrue(e.getMessage().contains("No se pudo generar el enlace"), e.getMessage());
    }

    @Test
    void unLinkVacioCortaConUnErrorDeDominio() {
        when(connectAccountService.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString())).thenReturn("   ");

        DomainException e = assertThrows(DomainException.class,
                () -> service.generateOnboardingLink(creator, "MX"));
        assertTrue(e.getMessage().contains("No se pudo generar el enlace"), e.getMessage());
    }

    @Test
    void conCuentaYaExistenteNoSeCreaUnaNuevaNiSeValidaElPais() {
        // El país de una cuenta ya existente está fijo: se ignora el pedido
        // (incluso si hoy no está en la lista blanca de Stripe).
        when(connectAccountService.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString()))
                .thenReturn("https://connect.stripe.com/setup/e/abc");

        assertEquals("https://connect.stripe.com/setup/e/abc",
                service.generateOnboardingLink(creator, "CO"));
        verify(connectAccountService, never()).createAccount(any(), anyString());
    }

    @Test
    void sinCuentaSeCreaUnaYElPaisDebeEstarEnLaListaBlanca() {
        when(connectAccountService.findByUser(creator)).thenReturn(Optional.empty());
        when(connectAccountService.createAccount(any(), anyString())).thenReturn(cuenta());
        when(gateway.createAccountLink(anyString(), anyString(), anyString()))
                .thenReturn("https://connect.stripe.com/setup/e/abc");

        assertEquals("https://connect.stripe.com/setup/e/abc",
                service.generateOnboardingLink(creator, "MX"));
        verify(connectAccountService).createAccount(any(), eq("MX"));
    }

    @Test
    void sinCuentaYConPaisNoSoportadoFallaAntesDeTocarStripe() {
        when(connectAccountService.findByUser(creator)).thenReturn(Optional.empty());

        UnsupportedPayoutCountryException e = assertThrows(UnsupportedPayoutCountryException.class,
                () -> service.generateOnboardingLink(creator, "CO"));
        assertTrue(e.getMessage().contains("Colombia"), e.getMessage());
        verify(connectAccountService, never()).createAccount(any(), anyString());
        verify(gateway, never()).createAccountLink(anyString(), anyString(), anyString());
    }

    private ConnectAccount cuenta() {
        return ConnectAccount.builder()
                .id(1L)
                .user(creator)
                .stripeAccountId("acct_1")
                .country("MX")
                .status(ConnectAccountStatus.ONBOARDING_INCOMPLETE)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
