package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.service.NotificationService;
import com.openlab.mx.share.payment.dto.CheckoutPayRequest;
import com.openlab.mx.share.payment.gateway.GatewaySession;
import com.openlab.mx.share.payment.service.StripeCheckoutService;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.RefundPolicy;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Checkout con Stripe (spec §5-6): el resumen se muestra con el total
 * desde el dominio; el POST crea reserva+payment PENDING y la Checkout
 * Session, y redirige a la página hosted de Stripe.
 */
@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private final StripeCheckoutService stripeCheckoutService;
    private final ReservationRepository reservationRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;
    private final RefundPolicy refundPolicy;

    @GetMapping("/checkout/{instanceId}")
    public String form(@PathVariable Long instanceId,
                       @RequestParam(defaultValue = "1") int attendees,
                       Model model) {
        ExperienceInstance instance = instance(instanceId);
        User user = currentUser.require();
        Reservation pendiente = pendiente(instanceId, user);
        // Si ya tiene reserva PENDIENTE (reintento / refresh), mostrar sus cupos.
        int cupos = pendiente != null ? pendiente.getAttendees() : sanitize(attendees);
        BigDecimal total = instance.getExperience().getPrice().multiply(BigDecimal.valueOf(cupos));

        model.addAttribute("instance", instance);
        model.addAttribute("attendees", cupos);
        model.addAttribute("total", total);
        model.addAttribute("currencyCode", instance.getExperience().getCurrencyCode() == null
                ? "MXN" : instance.getExperience().getCurrencyCode());
        // Política de reembolso visible antes de pagar (cartel) + el % que
        // corresponde a esta fecha si se cancela.
        model.addAttribute("refundPolicyLines", refundPolicy.lines());
        model.addAttribute("refundPercent", refundPolicy.percentFor(instance));
        return "checkout";
    }

    @PostMapping("/checkout/{instanceId}")
    public String pay(@PathVariable Long instanceId,
                      @Valid @ModelAttribute CheckoutPayRequest request,
                      BindingResult bindingResult,
                      RedirectAttributes redirectAttributes) {
        // Cupos fuera de 1..20 → se corrige al límite más cercano (como el GET).
        int cupos = bindingResult.hasErrors() ? 1 : sanitize(request.attendees());
        User user = currentUser.require();
        boolean nuevaReserva = pendiente(instanceId, user) == null;
        GatewaySession session;
        try {
            session = stripeCheckoutService.start(instanceId, cupos, user);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/checkout/" + instanceId + "?attendees=" + cupos;
        }
        if (nuevaReserva) {
            ExperienceInstance instance = instanceRepository.findById(instanceId)
                    .orElseThrow(() -> new DomainException("Fecha no encontrada"));
            User creador = instance.getExperience().getCreator();
            if (creador != null) {
                notificationService.notify(creador, NotificationType.SOLICITUD_RESERVA,
                        user.getUsername() + " reservó " + cupos + " lugar(es) en "
                                + instance.getExperience().getName(),
                        "/reservations");
            }
        }
        return "redirect:" + session.url();
    }

    private ExperienceInstance instance(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
    }

    private Reservation pendiente(Long instanceId, User user) {
        return reservationRepository
                .findFirstByInstanceIdAndUserIdAndStatus(instanceId, user.getId(),
                        ReservationStatus.PENDIENTE)
                .orElse(null);
    }

    private int sanitize(int attendees) {
        if (attendees < 1) {
            return 1;
        }
        return Math.min(attendees, 20);
    }
}
