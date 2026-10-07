package com.openlab.mx.share.reservation.controller;

import com.openlab.mx.share.reservation.dto.ReservationRequest;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.ReservationService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Reservas: abrir checkout, confirmar como creador, ver las propias
 * y la pantalla post-pago (spec §5.3).
 */
@Controller
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final CurrentUser currentUser;

    @Value("${escrow.commission-rate}")
    private java.math.BigDecimal commissionRate = new java.math.BigDecimal("0.05");

    @PostMapping("/reservations/{instanceId}")
    public String reserve(@PathVariable Long instanceId,
                          @Valid @ModelAttribute ReservationRequest request) {
        reservationService.reserve(instanceId, request.attendees(), currentUser.require());
        return "redirect:/checkout/" + instanceId;
    }

    /** El creador confirma la reserva pendiente (spec §5.3). */
    @PostMapping("/reservations/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reservationService.confirm(id, currentUser.require());
        redirectAttributes.addFlashAttribute("ok", "Reserva confirmada");
        return CurrentUser.backOr("/reservations");
    }

    @GetMapping("/reservations")
    public String myReservations(Model model) {
        User user = currentUser.require();
        model.addAttribute("reservations",
                reservationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        model.addAttribute("pendientes",
                reservationRepository.findByInstanceExperienceCreatorIdAndStatus(
                        user.getId(), ReservationStatus.PENDIENTE));
        return "reservations/my-reservations";
    }

    /** PRG post-checkout: muestra el último pago del usuario. */
    @GetMapping("/confirmation")
    public String confirmation(Model model) {
        User user = currentUser.require();
        Reservation reservation =
                reservationRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("reservation", reservation);
        if (reservation != null) {
            var experience = reservation.getInstance().getExperience();
            java.math.BigDecimal monto = experience.getPrice()
                    .multiply(java.math.BigDecimal.valueOf(reservation.getAttendees()));
            model.addAttribute("monto", monto);
            model.addAttribute("payout", monto.subtract(monto.multiply(commissionRate)));
            model.addAttribute("esCreador",
                    user.getId().equals(experience.getCreator().getId()));
        }
        return "confirmation";
    }
}
