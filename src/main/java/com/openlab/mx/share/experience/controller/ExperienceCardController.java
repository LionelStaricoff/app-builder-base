package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.dto.ExperienceDateDTO;
import com.openlab.mx.share.experience.dto.QuestionRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.service.ExperienceCardService;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.experience.service.QuestionService;
import com.openlab.mx.share.experience.service.RatingService;
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

/**
 * Fragmentos HTML que consume la card del feed.
 *
 * <p>Todo devuelve {@code text/html} ya renderizado (sintaxis
 * {@code template :: selector}), no JSON: el markup de fechas, preguntas y
 * reseñas vive una sola vez y la card lo inyecta tal cual. Así el scroll
 * infinito y la card inicial comparten el mismo contrato.
 */
@Controller
@RequiredArgsConstructor
public class ExperienceCardController {

    private final ExperienceCardService cardService;
    private final ExperienceService experienceService;
    private final QuestionService questionService;
    private final RatingService ratingService;
    private final CurrentUser currentUser;

    /** Panel de fechas: todas las futuras, con la elegida marcada. */
    @GetMapping("/experiences/{id}/dates")
    public String dates(@PathVariable Long id,
                        @RequestParam(required = false) Long instanceId,
                        Model model) {
        model.addAttribute("experienceId", id);
        model.addAttribute("dates", cardService.dates(id));
        model.addAttribute("selectedInstanceId", instanceId);
        return "experience/card-dates :: picker";
    }

    /** Disponibilidad de UNA fecha: el JS reemplaza el bloque de cupos. */
    @GetMapping("/experiences/{id}/dates/{instanceId}")
    public String availability(@PathVariable Long id,
                               @PathVariable Long instanceId,
                               Model model) {
        ExperienceDateDTO fecha = cardService.availability(id, instanceId);
        model.addAttribute("maxCapacity", fecha.maxCapacity());
        model.addAttribute("availableCapacity", fecha.availableCapacity());
        model.addAttribute("reservedCount", fecha.reservedCount());
        model.addAttribute("reservedAvatars", fecha.reservedAvatars());
        return "experience/card-dates :: availabilityContent";
    }

    @GetMapping("/experiences/{id}/questions")
    public String questions(@PathVariable Long id, Model model) {
        cargarPreguntas(id, currentUser.get(), model, null, null);
        return "experience/card-questions :: section";
    }

    /**
     * POST del form de preguntar dentro de la card: devuelve el fragmento
     * actualizado (pregunta nueva o error de validación) en vez de redirigir.
     * El form sin JS sigue posteando al endpoint normal.
     */
    @PostMapping("/experiences/{id}/questions/fragment")
    public String askFragment(@PathVariable Long id,
                              @Valid @ModelAttribute("questionRequest") QuestionRequest request,
                              BindingResult bindingResult,
                              Model model) {
        User usuario = currentUser.get();
        if (bindingResult.hasErrors()) {
            cargarPreguntas(id, usuario, model, null,
                    "Escribí una pregunta de entre 3 y 500 caracteres");
            return "experience/card-questions :: section";
        }
        try {
            questionService.ask(id, usuario, request.body());
            cargarPreguntas(id, usuario, model,
                    "Pregunta enviada: la respuesta queda publicada acá", null);
        } catch (DomainException e) {
            cargarPreguntas(id, usuario, model, null, e.getMessage());
        }
        return "experience/card-questions :: section";
    }

    @GetMapping("/experiences/{id}/reviews")
    public String reviews(@PathVariable Long id, Model model) {
        model.addAttribute("reviews", ratingService.reviewsFor(id));
        return "experience/card-reviews :: section";
    }

    private void cargarPreguntas(Long id, User usuario, Model model, String ok, String error) {
        Experience experience = experienceService.get(id);
        boolean esDueno = usuario != null && experience.getCreator() != null
                && usuario.getId().equals(experience.getCreator().getId());
        model.addAttribute("experienceId", id);
        model.addAttribute("questions", questionService.questionsFor(id));
        model.addAttribute("puedePreguntar", usuario != null && !esDueno);
        model.addAttribute("esDueno", esDueno);
        model.addAttribute("creatorName",
                experience.getCreator() == null ? null : experience.getCreator().getUsername());
        model.addAttribute("ok", ok);
        model.addAttribute("error", error);
    }
}
