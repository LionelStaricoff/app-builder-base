package com.openlab.mx.movo.payment.controller;

import com.openlab.mx.movo.payment.service.ConnectPayoutService;
import com.openlab.mx.movo.payment.service.StripeConnectService;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        log.info("[payouts] GET /creators/payouts — usuario={}", user.getUsername());
        Map<String, Object> status = stripeConnectService.getStatus(user);
        BigDecimal pendingAmount = connectPayoutService.getPendingAmount();
        log.info("[payouts] estado={} payoutsEnabled={} pendiente={}",
                status.get(StripeConnectService.KEY_STATUS), status.get(StripeConnectService.KEY_PAYOUTS_ENABLED), pendingAmount);

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
        log.info("[onboarding] GET /creators/onboarding — usuario={}", user.getUsername());
        Map<String, Object> status = stripeConnectService.getStatus(user);
        log.info("[onboarding] estado={} payoutsEnabled={}", status.get(StripeConnectService.KEY_STATUS), status.get(StripeConnectService.KEY_PAYOUTS_ENABLED));

        if (Boolean.TRUE.equals(status.get(StripeConnectService.KEY_PAYOUTS_ENABLED))) {
            log.info("[onboarding] payouts ya habilitados → redirect /creators/payouts");
            return "redirect:/creators/payouts";
        }

        model.addAttribute("status", status);
        // Selector de país de cobro: solo países donde Stripe permite cuentas.
        model.addAttribute("payoutCountries", stripeConnectService.supportedPayoutCountries());
        model.addAttribute("selectedCountry", stripeConnectService.resolvePayoutCountry(user));
        model.addAttribute("payoutCountryName",
                stripeConnectService.countryName((String) status.get(StripeConnectService.KEY_COUNTRY)));
        log.info("[onboarding] renderizando creators/onboarding");
        return "creators/onboarding";
    }

    /**
     * Callback de Stripe onboarding: vuelve a la página de cobros.
     */
    @GetMapping("/onboarding/complete")
    public String onboardingComplete() {
        log.info("[onboarding] GET /onboarding/complete — redirect /creators/payouts");
        return "redirect:/creators/payouts";
    }

    /**
     * Refresh de onboarding: vuelve a generar el link.
     */
    @GetMapping("/onboarding/refresh")
    public String onboardingRefresh() {
        log.info("[onboarding] GET /onboarding/refresh — redirect /creators/onboarding");
        return "redirect:/creators/onboarding";
    }
}
