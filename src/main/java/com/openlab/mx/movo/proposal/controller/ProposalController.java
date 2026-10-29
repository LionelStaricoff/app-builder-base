package com.openlab.mx.movo.proposal.controller;

import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.interest.entity.UserInterest;
import com.openlab.mx.movo.interest.service.InterestService;
import com.openlab.mx.movo.proposal.dto.ProposalForm;
import com.openlab.mx.movo.proposal.entity.ExperienceDraft;
import com.openlab.mx.movo.proposal.generator.ProposalGenerationException;
import com.openlab.mx.movo.proposal.generator.ProposalGenerator;
import com.openlab.mx.movo.proposal.service.ProposalService;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.user.entity.User;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Pantalla 3 del onboarding (spec §6): propuesta generada por IA que se edita
 * en la misma pantalla y "Crear experiencia" entrega el form prellenado.
 *
 * <p>Decisiones que viven acá y no en el servicio:
 *
 * <ul>
 *   <li>Whitelist de {@code next}: sólo "feed" o "new" (copiada de
 *       {@code OnboardingController.destino()}); cualquier otro valor cae a
 *       feed — nunca se refleja un destino de redirección del request.</li>
 *   <li>PRG en todos los POST: generar y crear redirigen al GET, así que
 *       F5 no reenvía nada y el flash lleva el error de generación.</li>
 *   <li>El fallback de prefill es el <em>último</em> borrador propio: un
 *       {@code ?draft=} inválido o ajeno se ignora y se cae al propio, en vez
 *       de 404 o de exponer el borrador del otro.</li>
 * </ul>
 */
@Controller
@RequestMapping("/onboarding/propuesta")
@RequiredArgsConstructor
public class ProposalController {

    /** Copy exacto de Global Constraints (plan §2). */
    static final String ERROR_GENERACION = "No pudimos generar la propuesta. Probá de nuevo.";

    private final CurrentUser currentUser;
    private final ProposalService service;
    private final InterestService interestService;
    private final ObjectProvider<ProposalGenerator> generatorProvider;

    @GetMapping
    public String pantalla(@RequestParam(defaultValue = "feed") String next,
                           @RequestParam(required = false) String draft,
                           Model model) {
        User user = currentUser.require();
        cargar(model, user, next, draft);
        // Spec §5: los campos salen prellenados del borrador (?draft= o el
        // último propio). Sin esto el select de categoría queda vacío y el
        // `required` ni siquiera manda el POST de "Crear experiencia".
        model.addAttribute("form", aForm((ExperienceDraft) model.getAttribute("borrador")));
        return "proposal/onboarding";
    }

    /** Contenido del borrador sobre un form vacío; sin borrador ⇒ form vacío. */
    static ProposalForm aForm(ExperienceDraft borrador) {
        if (borrador == null) {
            return new ProposalForm(null, null, null, null, null);
        }
        return new ProposalForm(borrador.getName(), borrador.getDescription(),
                borrador.getCategory(), borrador.getIncludes(), borrador.getBring());
    }

    /**
     * Genera SIEMPRE una fila nueva (INSERT): "Regenerar" acumula historia sin
     * pisar el borrador anterior. Éxito → PRG con {@code draft={id}}; fallo del
     * proveedor → PRG al GET con flash de error y sin fila a medias (el servicio
     * sólo persiste cuando el generador respondió).
     */
    @PostMapping("/generar")
    public String generar(@RequestParam(defaultValue = "feed") String next,
                          @RequestParam(required = false) String textoLibre,
                          RedirectAttributes redirect) {
        User user = currentUser.require();
        String destino = destino(next);
        try {
            ExperienceDraft draft = service.generar(user, textoLibre);
            return "redirect:/onboarding/propuesta?next=" + destino + "&draft=" + draft.getId();
        } catch (ProposalGenerationException e) {
            redirect.addFlashAttribute("error", ERROR_GENERACION);
            return "redirect:/onboarding/propuesta?next=" + destino;
        }
    }

    /**
     * Persiste la propuesta editada y cierra el onboarding de propuesta.
     * Errores de validación → vuelve a la pantalla con 200 y {@code th:errors}
     * (sin guardar nada).
     */
    @PostMapping("/crear")
    public String crear(@Valid @ModelAttribute("form") ProposalForm form,
                        BindingResult binding,
                        @RequestParam(name = "draft", required = false) String draft,
                        @RequestParam(defaultValue = "feed") String next,
                        Model model) {
        User user = currentUser.require();
        if (binding.hasErrors()) {
            cargar(model, user, next, draft);
            // Tras un POST con errores el form SIEMPRE se muestra, aunque el
            // borrador sea null: si no, th:errors quedaría invisible.
            model.addAttribute("mostrarForm", true);
            return "proposal/onboarding";
        }
        ExperienceDraft guardado = service.guardar(user, draft, form);
        service.marcarOnboarded(user);
        // Categoría y subtipo no se eligen en esta tarjeta: los elegiste en el
        // paso de intereses. El subtipo sale de ahí (interés de la categoría
        // elegida o, en su defecto, el primero) y viaja en la URL hasta el
        // form final; sin intereses ⇒ la URL va sin subtipo.
        Long subtipoId = subtipoSugerido(user, form.category());
        return "redirect:/experiences/new?draft=" + guardado.getId()
                + (subtipoId != null ? "&subtipo=" + subtipoId : "");
    }

    /** Primero el interés de la categoría del borrador; si no, el primer interés; sin ninguno ⇒ null. */
    private Long subtipoSugerido(User user, ExperienceCategory categoria) {
        List<UserInterest> intereses = interestService.intereses(user);
        return intereses.stream()
                .filter(i -> i.getSubrubro() != null && categoria != null
                        && categoria.name().equals(i.getSubrubro().getCategoria()))
                .findFirst()
                .or(() -> intereses.stream().filter(i -> i.getSubrubro() != null).findFirst())
                .map(i -> i.getSubrubro().getId())
                .orElse(null);
    }

    /**
     * Cierra el onboarding sin crear experiencia (flag=true) y sale por la
     * whitelist. Es un POST sin CSRF-extra: misma protección que los demás.
     */
    @PostMapping("/saltear")
    public String saltear(@RequestParam(required = false) String next) {
        User user = currentUser.require();
        service.marcarOnboarded(user);
        return "redirect:" + ruta(next);
    }

    /** Atributos comunes del render: borrador prellenado, intereses y estado del generador. */
    private void cargar(Model model, User user, String next, String draftParam) {
        ExperienceDraft borrador = service.borradorPropio(user, draftParam)
                .or(() -> service.ultimoBorrador(user))
                .orElse(null);
        model.addAttribute("next", destino(next));
        model.addAttribute("borrador", borrador);
        model.addAttribute("draftId", borrador != null ? borrador.getId() : null);
        model.addAttribute("intereses", interestService.intereses(user));
        // Sin borrador no hay propuesta todavía: el form de edición se oculta
        // (spec §5, "tras propuesta"). El error de validación lo re-muestra
        // pase lo que pase (ver crear()).
        model.addAttribute("mostrarForm", borrador != null);
        // Sin proveedor configurado (p.ej. spring.ai.model.chat=none) el botón
        // queda deshabilitado en vez de fallar en el POST.
        model.addAttribute("generatorPresent", generatorProvider.getIfAvailable() != null);
    }

    /** Whitelist: sólo "feed"/"new"; el resto (incluido {@code //evil.com}) ⇒ feed. */
    static String destino(String next) {
        return "new".equals(next) ? "new" : "feed";
    }

    /** Camino local equivalente para el redirect de saltear. */
    static String ruta(String next) {
        return "new".equals(destino(next)) ? "/experiences/new" : "/feed";
    }
}