package com.openlab.mx.movo.payment.service;

import com.openlab.mx.movo.payment.entity.ConnectAccount;
import com.openlab.mx.movo.payment.entity.ConnectAccountStatus;
import com.openlab.mx.movo.payment.gateway.PaymentGateway;
import com.openlab.mx.movo.payment.repository.ConnectAccountRepository;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.user.entity.Role;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El estado de la cuenta Connect vive en Stripe, no en la base: cada sync
 * consulta la API y recién después guarda el estado local.
 */
class ConnectAccountServiceTest {

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final ConnectAccountRepository repository = mock(ConnectAccountRepository.class);
    private final ConnectAccountService service = new ConnectAccountService(repository, gateway);

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
        when(repository.save(any(ConnectAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearCuentaGuardaElIdDeStripeYElPais() {
        when(gateway.createConnectAccount("MX", "creador@example.com")).thenReturn("acct_nuevo");

        ConnectAccount cuenta = service.createAccount(creator, "MX");

        assertEquals("acct_nuevo", cuenta.getStripeAccountId());
        assertEquals("MX", cuenta.getCountry());
        assertEquals(ConnectAccountStatus.ONBOARDING_INCOMPLETE, cuenta.getStatus());
        assertNotNull(cuenta.getCreatedAt());
        verify(repository).save(cuenta);
    }

    @Test
    void sincronizarActivaLaCuentaCuandoStripeCobraYPaga() {
        ConnectAccount cuenta = cuenta();
        when(repository.findByUser(creator)).thenReturn(Optional.of(cuenta));
        when(gateway.retrieveConnectAccount("acct_1")).thenReturn(
                new PaymentGateway.ConnectAccountInfo("acct_1", true, true, null));

        ConnectAccount resultado = service.syncAccountStatus(creator);

        assertEquals(ConnectAccountStatus.ACTIVE, resultado.getStatus());
        assertNotNull(resultado.getLastSyncedAt());
    }

    @Test
    void sincronizarMarcaRestringidaSiStripeTraeElMotivo() {
        ConnectAccount cuenta = cuenta();
        when(repository.findByUser(creator)).thenReturn(Optional.of(cuenta));
        when(gateway.retrieveConnectAccount("acct_1")).thenReturn(
                new PaymentGateway.ConnectAccountInfo("acct_1", false, false, "requirements_pending"));

        assertEquals(ConnectAccountStatus.RESTRICTED, service.syncAccountStatus(creator).getStatus());
    }

    @Test
    void sincronizarSinCuentaLocalEsUnErrorDeDominio() {
        when(repository.findByUser(creator)).thenReturn(Optional.empty());

        DomainException e = assertThrows(DomainException.class, () -> service.syncAccountStatus(creator));
        assertTrue(e.getMessage().contains("No hay cuenta de cobro configurada"), e.getMessage());
    }

    @Test
    void puedeRecibirPagosSoloConLaCuentaActiva() {
        ConnectAccount cuenta = cuenta();
        cuenta.setStatus(ConnectAccountStatus.ACTIVE);
        when(repository.findByUser(creator)).thenReturn(Optional.of(cuenta));
        assertTrue(service.canReceivePayouts(creator));

        cuenta.setStatus(ConnectAccountStatus.ONBOARDING_INCOMPLETE);
        assertFalse(service.canReceivePayouts(creator));
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
