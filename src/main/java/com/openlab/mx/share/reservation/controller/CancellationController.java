package com.openlab.mx.share.reservation.controller;

import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.reservation.service.CancellationService;
import com.openlab.mx.share.reservation.service.RefundPolicy;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Los 3 casos de cancelación (spec §5.4) vía POST + PRG.
 */
@Controller
@RequiredArgsConstructor
public class CancellationController {

    private final CancellationService cancellationService;
    private final ExperienceInstanceRepository instanceRepository;
    private final CurrentUser currentUser;
    private final RefundPolicy refundPolicy;

    /** Caso 1: el participante cancela su reserva. */
    @PostMapping("/reservations/{id}/cancel")
    public String cancelByParticipant(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var resultado = cancellationService.cancelByParticipant(id, currentUser.require());
        redirectAttributes.addFlashAttribute("ok", "Reserva cancelada. Se devuelve el "
                + refundPolicy.percentOf(resultado.refundRate()) + "% del monto.");
        return CurrentUser.backOr("/reservations");
    }

    /** Caso 2: el creador cancela una fecha. */
    @PostMapping("/instances/{id}/cancel")
    public String cancelByCreator(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        cancellationService.cancelByCreator(id, currentUser.require());
        redirectAttributes.addFlashAttribute("ok", "Fecha cancelada y cupos reembolsados");
        return CurrentUser.backOr(experienceLink(id));
    }

    /** Caso 3 manual: cupo mínimo, solo el dueño. */
    @PostMapping("/instances/{id}/cancel-min-capacity")
    public String cancelByMinCapacity(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var instance = instanceRepository.findById(id)
                .orElseThrow(() -> new DomainException("Fecha no encontrada"));
        var dueno = instance.getExperience().getCreator();
        var usuario = currentUser.require();
        if (dueno == null || !dueno.getId().equals(usuario.getId())) {
            throw new NotOwnerException();
        }
        cancellationService.cancelByMinCapacity(id);
        redirectAttributes.addFlashAttribute("ok", "Fecha cancelada por cupo mínimo");
        return CurrentUser.backOr(experienceLink(id));
    }

    private String experienceLink(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .map(i -> "/experiences/" + i.getExperience().getId())
                .orElse("/feed");
    }
}
