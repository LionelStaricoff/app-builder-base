package com.openlab.mx.share.payment.controller;

import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.reservation.dto.CheckoutRequest;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.ReservationService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Checkout simulado (spec §5.3): la tarjeta no se valida más allá del
 * formato; el pago queda retenido en escrow hasta la liberación.
 */
@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final CurrentUser currentUser;

    @GetMapping("/checkout/{instanceId}")
    public String form(@PathVariable Long instanceId, Model model) {
        ExperienceInstance instance = instance(instanceId);
        User user = currentUser.require();
        Reservation pendiente = pendiente(instanceId, user);
        int attendees = pendiente != null ? pendiente.getAttendees() : 1;
        BigDecimal total = instance.getExperience().getPrice().multiply(BigDecimal.valueOf(attendees));

        model.addAttribute("instance", instance);
        model.addAttribute("attendees", attendees);
        model.addAttribute("total", total);
        model.addAttribute("checkoutRequest", new CheckoutRequest("", "", "", ""));
        return "checkout";
    }

    @PostMapping("/checkout/{instanceId}")
    public String pay(@PathVariable Long instanceId,
                      @Valid @ModelAttribute CheckoutRequest checkoutRequest,
                      BindingResult bindingResult,
                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revisá los datos de la tarjeta");
            return "redirect:/checkout/" + instanceId;
        }
        User user = currentUser.require();
        if (pendiente(instanceId, user) == null) {
            reservationService.reserve(instanceId, 1, user);
        }
        return "redirect:/confirmation";
    }

    private ExperienceInstance instance(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
    }

    private Reservation pendiente(Long instanceId, User user) {
        return reservationRepository
                .findFirstByInstanceIdAndUserIdAndStatus(instanceId, user.getId(), ReservationStatus.PENDIENTE)
                .orElse(null);
    }
}
