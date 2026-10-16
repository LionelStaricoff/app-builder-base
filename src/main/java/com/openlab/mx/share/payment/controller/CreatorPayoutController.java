package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.payment.entity.ConnectPayout;
import com.openlab.mx.share.payment.service.ConnectPayoutService;
import com.openlab.mx.share.payment.service.StripeConnectService;
import com.openlab.mx.share.shared.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints para que los creadores gestionen sus cobros vía Stripe Connect.
 * Todos requieren autenticación (cualquier usuario logueado puede reclamar).
 */
@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorPayoutController {

    private final StripeConnectService stripeConnectService;
    private final ConnectPayoutService connectPayoutService;
    private final CurrentUser currentUser;

    /**
     * Estado de la cuenta Connect del creador + monto pendiente.
     */
    @GetMapping("/payout-account/status")
    public Map<String, Object> getStatus() {
        return stripeConnectService.getStatus(currentUser.require());
    }

    /**
     * Genera un link temporal de Hosted Onboarding para que el creador
     * configure su cuenta Connect. Si ya tiene cuenta, regenera el link.
     */
    @PostMapping("/payout-account/onboarding-link")
    public Map<String, String> generateOnboardingLink() {
        String url = stripeConnectService.generateOnboardingLink(currentUser.require());
        return Map.of("url", url);
    }

    /**
     * El creador reclama todos sus pagos pendientes.
     * Ejecuta un transfer batch a su cuenta Connect.
     */
    @PostMapping("/payouts/request")
    public Map<String, Object> requestPayout() {
        return connectPayoutService.requestPayout();
    }

    /**
     * Historial de desembolsos del creador.
     */
    @GetMapping("/payouts/history")
    public List<ConnectPayout> getPayoutHistory() {
        return connectPayoutService.getPayoutHistory();
    }
}
