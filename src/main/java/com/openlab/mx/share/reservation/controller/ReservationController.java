package com.openlab.mx.share.reservation.controller;

import com.openlab.mx.share.reservation.dto.ReservationRequest;
import com.openlab.mx.share.reservation.entity.Reservation;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.reservation.service.CalendarIcsService;
import com.openlab.mx.share.reservation.service.RefundPolicy;
import com.openlab.mx.share.reservation.service.ReservationService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
    private final com.openlab.mx.share.identity.service.RequireVerifiedIdentityService
            requireVerifiedIdentityService;
    private final RefundPolicy refundPolicy;
    private final CalendarIcsService calendarIcsService;

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

    @GetMapping("/reservations")
    public String myReservations(Model model) {
        User user = currentUser.require();
        var reservas = reservationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("reservations", reservas);
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
            boolean esCreador = user.getId().equals(experience.getCreator().getId());
            model.addAttribute("esCreador", esCreador);
            // El chat grupal se abre recién con la reserva CONFIRMADA (o siempre
            // para el creador): mostrar el link a ciegas dejaría un 403 post-pago.
            model.addAttribute("chatDisponible",
                    esCreador || reservation.getStatus() == ReservationStatus.CONFIRMADA);
        }
        return "confirmation";
    }

    /** Descarga .ics de la reserva: "añadir a mi calendario" post-pago. */
    @GetMapping("/reservations/{id}/calendar.ics")
    public ResponseEntity<String> calendar(@PathVariable Long id) {
        User user = currentUser.require();
        Reservation reserva = reservationRepository.findById(id)
                .orElseThrow(() -> new DomainException("Reserva no encontrada"));
        boolean dueno = reserva.getUser() != null
                && user.getId().equals(reserva.getUser().getId());
        boolean creador = reserva.getInstance() != null
                && reserva.getInstance().getExperience() != null
                && reserva.getInstance().getExperience().getCreator() != null
                && user.getId().equals(reserva.getInstance().getExperience().getCreator().getId());
        if (!dueno && !creador) {
            throw new NotOwnerException();
        }
        String archivo = "shared-" + reserva.getInstance().getDate()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".ics";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .body(calendarIcsService.build(reserva));
    }
}
