package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.payment.service.StripeCheckoutService;
import com.openlab.mx.share.shared.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Retorno desde Stripe (spec §6): la URL hosteada devuelve acá con
 * session_id, que el back revalida contra la puerta real (nunca se
 * confía en el query param) antes de PENDING→HELD y PRG a /confirmation.
 */
@Controller
@RequiredArgsConstructor
public class PaymentSuccessController {

    private final StripeCheckoutService stripeCheckoutService;
    private final CurrentUser currentUser;

    @GetMapping("/pago/exito")
    public String exito(@RequestParam("session_id") String sessionId) {
        stripeCheckoutService.complete(sessionId, currentUser.require().getId());
        return "redirect:/confirmation";
    }
}
