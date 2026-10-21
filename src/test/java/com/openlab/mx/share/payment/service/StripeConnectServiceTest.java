package com.openlab.mx.share.payment.service;

import com.openlab.mx.share.payment.entity.ConnectAccount;
import com.openlab.mx.share.payment.entity.ConnectAccountStatus;
import com.openlab.mx.share.payment.gateway.PaymentGateway;
import com.openlab.mx.share.payment.repository.ConnectAccountRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El enlace de onboarding es lo que manda al creador a Stripe. Si la pasarela
 * devuelve un link vacío, el controller reventaba con NullPointerException
 * (500 sin mensaje). Ahora el servicio lo corta con un error de dominio.
 */
class StripeConnectServiceTest {

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final ConnectAccountRepository repository = mock(ConnectAccountRepository.class);
    private final StripeConnectService service = new StripeConnectService(repository, gateway);

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
        when(repository.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString()))
                .thenReturn("https://connect.stripe.com/setup/e/abc");

        assertEquals("https://connect.stripe.com/setup/e/abc",
                service.generateOnboardingLink(creator, "MX"));
    }

    @Test
    void unLinkNullCortaConUnErrorDeDominioYNoConNullPointer() {
        when(repository.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString())).thenReturn(null);

        DomainException e = assertThrows(DomainException.class,
                () -> service.generateOnboardingLink(creator, "MX"));
        assertTrue(e.getMessage().contains("No se pudo generar el enlace"), e.getMessage());
    }

    @Test
    void unLinkVacioCortaConUnErrorDeDominio() {
        when(repository.findByUser(creator)).thenReturn(Optional.of(cuenta()));
        when(gateway.createAccountLink(anyString(), anyString(), anyString())).thenReturn("   ");

        DomainException e = assertThrows(DomainException.class,
                () -> service.generateOnboardingLink(creator, "MX"));
        assertTrue(e.getMessage().contains("No se pudo generar el enlace"), e.getMessage());
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
