package com.openlab.mx.movo.interest.controller;

import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.Subrubro;
import com.openlab.mx.movo.experience.repository.SubrubroRepository;
import com.openlab.mx.movo.interest.dto.InterestLevelsForm;
import com.openlab.mx.movo.interest.entity.InterestLevel;
import com.openlab.mx.movo.interest.service.InterestService;
import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Onboarding de intereses en dos pasos: qué rubros le gustan al usuario y, por
 * cada uno, cuánto sabe. También sirve para editar desde Ajustes, porque ambos
 * pasos arrancan precargados con lo que el usuario ya tenía guardado.
 */
@Controller
@RequestMapping("/onboarding/intereses")
@RequiredArgsConstructor
public class OnboardingController {

    private static final String PASO_1 = "interest/onboarding";
    private static final String PASO_2 = "interest/levels";

    private final InterestService interestService;
    private final SubrubroRepository subrubroRepository;
    private final CurrentUser currentUser;

    @GetMapping
    public String pasoUno(@RequestParam(required = false) String next, Model model) {
        return paso1(model, destino(next), null);
    }

    @PostMapping
    public String pasoDos(@RequestParam(required = false) List<Long> subrubroIds,
                          @RequestParam(required = false) String next,
                          Model model) {
        String destino = destino(next);
        User user = currentUser.require();

        List<Long> ids = subrubroIds == null ? List.of()
                : subrubroIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return paso1(model, destino, "Elegí al menos un interés para continuar");
        }
        if (ids.size() > InterestService.MAX_INTERESES) {
            return paso1(model, destino,
                    "Podés elegir hasta " + InterestService.MAX_INTERESES + " intereses");
        }

        List<Subrubro> elegidos = interestService.ordenar(subrubroRepository.findAllById(ids));
        if (elegidos.size() != ids.size()) {
            return paso1(model, destino, "Subrubro inválido");
        }

        model.addAttribute("paso", 2);
        model.addAttribute("next", destino);
        model.addAttribute("elegidos", elegidos);
        model.addAttribute("niveles", nivelesConDefault(user, elegidos));
        model.addAttribute("nivelesDisponibles", InterestLevel.values());
        model.addAttribute("rubroLabels", Arrays.stream(ExperienceCategory.values())
                .collect(Collectors.toMap(Enum::name, ExperienceCategory::getLabel)));
        return PASO_2;
    }

    /** El paso 2 nunca ve un nivel nulo: sin valor previo arranca en CURIOSO. */
    private Map<Long, InterestLevel> nivelesConDefault(User user, List<Subrubro> elegidos) {
        Map<Long, InterestLevel> guardados = interestService.niveles(user);
        Map<Long, InterestLevel> niveles = new LinkedHashMap<>();
        for (Subrubro subrubro : elegidos) {
            niveles.put(subrubro.getId(),
                    guardados.getOrDefault(subrubro.getId(), InterestLevel.CURIOSO));
        }
        return niveles;
    }

    @PostMapping("/niveles")
    public String guardar(@ModelAttribute InterestLevelsForm form,
                          @RequestParam(required = false) String next,
                          Model model) {
        String destino = destino(next);
        Map<Long, InterestLevel> niveles = form.nivelesPorSubrubroId();
        try {
            interestService.guardar(currentUser.require(), form.getSubrubroIds(), niveles);
        } catch (DomainException e) {
            return paso1(model, destino, e.getMessage());
        }
        return "redirect:" + ruta(destino);
    }

    @PostMapping("/saltear")
    public String saltear(@RequestParam(required = false) String next) {
        String destino = destino(next);
        interestService.guardar(currentUser.require(), List.of(), Map.of());
        return "redirect:" + ruta(destino);
    }

    private String paso1(Model model, String destino, String error) {
        model.addAttribute("paso", 1);
        model.addAttribute("next", destino);
        model.addAttribute("max", InterestService.MAX_INTERESES);
        model.addAttribute("rubros", interestService.catalogo());
        model.addAttribute("seleccionados", interestService.idsSeleccionados(currentUser.require()));
        if (error != null) {
            model.addAttribute("error", error);
        }
        return PASO_1;
    }

    /** Whitelist: {@code next} viene de la URL, nunca redirigimos a un valor crudo. */
    private static String destino(String next) {
        return "settings".equals(next) ? "settings" : "feed";
    }

    private static String ruta(String destino) {
        return "settings".equals(destino) ? "/settings" : "/feed";
    }
}
