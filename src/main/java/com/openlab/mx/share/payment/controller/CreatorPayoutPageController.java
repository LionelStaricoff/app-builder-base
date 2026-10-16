package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.payment.service.ConnectPayoutService;
import com.openlab.mx.share.payment.service.StripeConnectService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Páginas del flujo de cobros del creador (Stripe Connect).
 * Muestra estado de cuenta, monto pendiente y botón para reclamar.
 */
@Controller
@RequiredArgsConstructor
public class CreatorPayoutPageController {

    private final StripeConnectService stripeConnectService;
    private final ConnectPayoutService connectPayoutService;
    private final CurrentUser currentUser;

    /**
     * Página principal de cobros: estado de cuenta Connect + monto pendiente.
     */
    @GetMapping("/creators/payouts")
    public String payoutPage(Model model) {
        User user = currentUser.require();
        Map<String, Object> status = stripeConnectService.getStatus(user);
        BigDecimal pendingAmount = connectPayoutService.getPendingAmount();

        model.addAttribute("status", status);
        model.addAttribute("pendingAmount", pendingAmount);
        return "creators/payout";
    }

    /**
     * Página de onboarding: genera el link y redirige a Stripe.
     * Si ya está activo, vuelve a la página de cobros.
     */
    @GetMapping("/creators/onboarding")
    public String onboarding(Model model) {
        User user = currentUser.require();
        Map<String, Object> status = stripeConnectService.getStatus(user);

        if (Boolean.TRUE.equals(status.get("payoutsEnabled"))) {
            return "redirect:/creators/payouts";
        }

        model.addAttribute("status", status);
        return "creators/onboarding";
    }

    /**
     * Callback de Stripe onboarding: vuelve a la página de cobros.
     */
    @GetMapping("/onboarding/complete")
    public String onboardingComplete() {
        return "redirect:/creators/payouts";
    }

    /**
     * Refresh de onboarding: vuelve a generar el link.
     */
    @GetMapping("/onboarding/refresh")
    public String onboardingRefresh() {
        return "redirect:/creators/onboarding";
    }
}
