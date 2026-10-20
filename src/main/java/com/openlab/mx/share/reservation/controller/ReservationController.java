package com.openlab.mx.share.reservation.controller;

import com.openlab.mx.share.notification.entity.NotificationType;
import com.openlab.mx.share.notification.service.NotificationService;
import com.openlab.mx.share.reservation.dto.ReservationRequest;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.RefundPolicy;
import com.openlab.mx.share.reservation.service.ReservationService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
    private final NotificationService notificationService;
    private final CurrentUser currentUser;
    private final com.openlab.mx.share.identity.service.RequireVerifiedIdentityService
            requireVerifiedIdentityService;
    private final RefundPolicy refundPolicy;

    @Value("${escrow.commission-rate}")
    private java.math.BigDecimal commissionRate = new java.math.BigDecimal("0.05");

    @PostMapping("/reservations/{instanceId}")
    public String reserve(@PathVariable Long instanceId,
                          @Valid @ModelAttribute ReservationRequest request,
                          BindingResult bindingResult) {
        // Opción A: sin pago no hay reserva. Este paso solo lleva al checkout
        // con los cupos elegidos, no persiste nada en DB.
        // KYC (Didit): el primer paso de reservar ya exige identidad verificada
        // (el service de reserva también lo exige al persistir).
        User user = currentUser.require();
        requireVerifiedIdentityService.requireVerified(user.getId());
        int attendees = request != null ? request.attendees() : 1;
        if (bindingResult.hasErrors() || attendees < 1) {
            attendees = 1;
        }
        if (attendees > 20) {
            attendees = 20;
        }
        return "redirect:/checkout/" + instanceId + "?attendees=" + attendees;
    }

    /** El creador confirma la reserva pendiente (spec §5.3). */
    @PostMapping("/reservations/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Reservation reservation = reservationService.confirm(id, currentUser.require());
        notificationService.notify(reservation.getUser(), NotificationType.RESERVA_CONFIRMADA,
                "Tu reserva en " + reservation.getInstance().getExperience().getName()
                        + " fue confirmada",
                "/reservations");
        redirectAttributes.addFlashAttribute("ok", "Reserva confirmada");
        return CurrentUser.backOr("/reservations");
    }

    @GetMapping("/reservations")
    public String myReservations(Model model) {
        User user = currentUser.require();
        var reservas = reservationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("reservations", reservas);
        model.addAttribute("pendientes",
                reservationRepository.findByInstanceExperienceCreatorIdAndStatus(
                        user.getId(), ReservationStatus.PENDIENTE));
        // % a devolver por reserva: el botón de cancelar lo dice antes de aplicar.
        java.util.Map<Long, Integer> reembolso = new java.util.HashMap<>();
        for (Reservation reserva : reservas) {
            if (reserva.getInstance() != null) {
                reembolso.put(reserva.getId(), refundPolicy.percentFor(reserva.getInstance()));
            }
        }
        model.addAttribute("refundPercent", reembolso);
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
            model.addAttribute("currencyCode", experience.getCurrencyCode() == null
                    ? "MXN" : experience.getCurrencyCode());
            model.addAttribute("esCreador",
                    user.getId().equals(experience.getCreator().getId()));
        }
        return "confirmation";
    }
}
