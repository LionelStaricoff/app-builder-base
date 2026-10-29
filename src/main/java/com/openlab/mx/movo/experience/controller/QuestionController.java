package com.openlab.mx.movo.experience.controller;

import com.openlab.mx.movo.experience.dto.AnswerRequest;
import com.openlab.mx.movo.experience.dto.QuestionRequest;
import com.openlab.mx.movo.experience.service.QuestionService;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.shared.exception.DomainException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Preguntas públicas del detalle: preguntar (cualquier cuenta) y responder
 * (solo la anfitriona). Ambos POST vuelven al ancla #preguntas con flash.
 */
@Controller
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final CurrentUser currentUser;

    @PostMapping("/experiences/{id}/questions")
    public String ask(@PathVariable Long id,
                      @Valid @ModelAttribute("questionRequest") QuestionRequest request,
                      BindingResult bindingResult,
                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    "Escribí una pregunta de entre 3 y 500 caracteres");
            return "redirect:/experiences/" + id + "#preguntas";
        }
        try {
            questionService.ask(id, currentUser.require(), request.body());
            redirectAttributes.addFlashAttribute("ok",
                    "Pregunta enviada: la respuesta queda publicada acá");
        } catch (DomainException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/experiences/" + id + "#preguntas";
    }

    @PostMapping("/questions/{id}/answer")
    public String answer(@PathVariable Long id,
                         @Valid @ModelAttribute("answerRequest") AnswerRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        Long experienceId;
        try {
            experienceId = questionService.experienceIdOf(id);
        } catch (DomainException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/feed";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    "La respuesta tiene que tener entre 1 y 500 caracteres");
            return "redirect:/experiences/" + experienceId + "#preguntas";
        }
        try {
            questionService.answer(id, currentUser.require(), request.answer());
            redirectAttributes.addFlashAttribute("ok", "Respuesta publicada");
        } catch (DomainException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/experiences/" + experienceId + "#preguntas";
    }
}
