package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.RatingService;
import com.openlab.mx.share.shared.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Evaluación al terminar una experiencia: estrella 1..5 + comentario (spec §5.3).
 */
@Controller
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;
    private final ExperienceInstanceRepository instanceRepository;
    private final CurrentUser currentUser;

    @PostMapping("/instances/{id}/rate")
    public String rate(@PathVariable Long id,
                       @RequestParam int stars,
                       @RequestParam(defaultValue = "") String text,
                       RedirectAttributes redirectAttributes) {
        Long experienceId = instanceRepository.findById(id)
                .map(i -> i.getExperience().getId())
                .orElseThrow(() -> new com.openlab.mx.share.shared.exception.DomainException("Fecha no encontrada"));

        if (stars < 1 || stars > 5) {
            redirectAttributes.addFlashAttribute("error", "Elegí entre 1 y 5 estrellas");
            return "redirect:/experiences/" + experienceId;
        }
        ratingService.rate(id, currentUser.require(), stars, text);
        redirectAttributes.addFlashAttribute("ok", "¡Gracias por tu evaluación!");
        return "redirect:/experiences/" + experienceId;
    }
}
