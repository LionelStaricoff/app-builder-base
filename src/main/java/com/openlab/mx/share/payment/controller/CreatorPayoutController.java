package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.payment.entity.ConnectPayout;
import com.openlab.mx.share.payment.service.ConnectPayoutService;
import com.openlab.mx.share.payment.service.StripeConnectService;
import com.openlab.mx.share.shared.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CreatorPayoutController {

    private final StripeConnectService stripeConnectService;
    private final ConnectPayoutService connectPayoutService;
    private final CurrentUser currentUser;

    /**
     * Estado de la cuenta Connect del creador + monto pendiente.
     */
    @GetMapping("/payout-account/status")
    public Map<String, Object> getStatus() {
        var user = currentUser.require();
        log.info("[api] GET /payout-account/status — usuario={}", user.getUsername());
        Map<String, Object> status = stripeConnectService.getStatus(user);
        log.info("[api] status response={}", status);
        return status;
    }

    /**
     * Genera un link temporal de Hosted Onboarding para que el creador
     * configure su cuenta Connect. Si ya tiene cuenta, regenera el link.
     */
    @PostMapping("/payout-account/onboarding-link")
    public Map<String, String> generateOnboardingLink() {
        var user = currentUser.require();
        log.info("[api] POST /payout-account/onboarding-link — usuario={}", user.getUsername());
        String url = stripeConnectService.generateOnboardingLink(user);
        log.info("[api] account link generado (long={}) url={}...", url.length(),
                url.substring(0, Math.min(url.length(), 60)));
        return Map.of("url", url);
    }

    /**
     * El creador reclama todos sus pagos pendientes.
     * Ejecuta un transfer batch a su cuenta Connect.
     */
    @PostMapping("/payouts/request")
    public Map<String, Object> requestPayout() {
        var user = currentUser.require();
        log.info("[api] POST /payouts/request — usuario={}", user.getUsername());
        Map<String, Object> result = connectPayoutService.requestPayout();
        log.info("[api] payout result={}", result);
        return result;
    }

    /**
     * Historial de desembolsos del creador.
     */
    @GetMapping("/payouts/history")
    public List<ConnectPayout> getPayoutHistory() {
        return connectPayoutService.getPayoutHistory();
    }
}
