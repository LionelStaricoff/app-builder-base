package com.openlab.mx.share.identity.controller;

import com.openlab.mx.share.identity.dto.VerificationStatusResponse;
import com.openlab.mx.share.identity.service.VerificationSessionService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Páginas del flujo hosteado: /verificacion (estado + botón de inicio) y
 * /verificacion/callback (a donde Didit devuelve al usuario; el JS revalida
 * contra el back, NUNCA contra los query params de Didit).
 */
@Controller
@RequiredArgsConstructor
public class VerificationPageController {

    private final VerificationSessionService sessionService;
    private final CurrentUser currentUser;

    @GetMapping("/verificacion")
    public String verificacion(@RequestParam(name = "returnUrl", required = false) String returnUrl,
                               Model model) {
        User user = currentUser.require();
        VerificationStatusResponse status = sessionService.getStatus(user);
        model.addAttribute("status", status.status());
        model.addAttribute("verifiedAt", status.verifiedAt());
        model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
        return "verification/start";
    }

    @GetMapping("/verificacion/callback")
    public String callback(@RequestParam(name = "returnUrl", required = false) String returnUrl,
                           Model model) {
        model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
        return "verification/callback";
    }

    /** Anti open-redirect: solo paths locales (/algo, no //host ni relativos). */
    static String safeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()
                || !returnUrl.startsWith("/") || returnUrl.startsWith("//")) {
            return "/feed";
        }
        return returnUrl;
    }
}
