package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ExperienceStopDTO;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceImage;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.experience.service.RatingService;
import com.openlab.mx.share.reservation.service.RefundPolicy;
import com.openlab.mx.share.shared.JacksonHelper;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.ZoneId;import java.time.LocalDate;
import java.time.ZoneId;import java.time.LocalTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;
    private final UserRepository userRepository;
    private final ExperienceImageRepository imageRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final com.openlab.mx.share.experience.repository.ExperienceScheduleRepository scheduleRepository;
    private final JacksonHelper jacksonHelper;
    private final RefundPolicy refundPolicy;
    private final RatingService ratingService;

    @GetMapping("/experiences/new")
    public String form(Model model) {
        model.addAttribute("experienceForm", emptyForm());
        model.addAttribute("experienceCurrency", currentUser().getCurrencyCode());
        model.addAttribute("stops", List.of());
        return "experience/form";
    }

    @PostMapping("/experiences")
    @PreAuthorize("hasRole('CREATOR')")
    public String create(@Valid @ModelAttribute("experienceForm") ExperienceFormRequest request,
                         BindingResult bindingResult,
                         @RequestParam(required = false) String currencyCode,
                         @RequestParam(required = false, name = "stopPlace") List<String> stopPlace,
                         @RequestParam(required = false, name = "stopCity") List<String> stopCity,
                         @RequestParam(required = false, name = "stopCountry") List<String> stopCountry,
                         @RequestParam(required = false, name = "stopLat") List<Double> stopLat,
                         @RequestParam(required = false, name = "stopLng") List<Double> stopLng,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            repopulateForm(model, currencyCode,
                    toStops(stopPlace, stopCity, stopCountry, stopLat, stopLng));
            return "experience/form";
        }
        try {
            Experience experience = experienceService.create(request, currentUser(),
                    currencyCode, toStops(stopPlace, stopCity, stopCountry, stopLat, stopLng));
            return "redirect:/experiences/" + experience.getId();
        } catch (com.openlab.mx.share.shared.exception.UnverifiedIdentityException e) {
            // KYC (Didit): no se traga con el catch de DomainException de
            // abajo; el GlobalExceptionHandler redirige a /verificacion.
            throw e;
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            repopulateForm(model, currencyCode,
                    toStops(stopPlace, stopCity, stopCountry, stopLat, stopLng));
            return "experience/form";
        }
    }

    @GetMapping("/experiences/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Experience experience = experienceService.get(id);
        List<ExperienceImage> images = imageRepository.findByExperienceIdOrderByIdAsc(id);
        List<ExperienceInstance> instances = instanceRepository
                .findByExperienceIdAndDateGreaterThanEqualOrderByDateAsc(id, LocalDate.now(ZoneId.systemDefault())).stream()
                .filter(i -> i.getStatus() == InstanceStatus.SCHEDULED)
                .toList();
        model.addAttribute("experience", experience);
        model.addAttribute("experienceCurrency",
                experience.getCurrencyCode() == null ? "MXN" : experience.getCurrencyCode());
        var stops = experienceService.stopsOf(id);
        model.addAttribute("stops", stops);
        model.addAttribute("stopsJson", jacksonHelper.toJson(stops));
        model.addAttribute("images", images);
        model.addAttribute("instances", instances);
        // Reseñas con texto (Rating + Comment de fechas terminadas).
        model.addAttribute("reviews", ratingService.reviewsFor(id));
        // Política de reembolso visible ANTES de reservar (cartel) y el % que
        // corresponde a cada fecha.
        model.addAttribute("refundPolicyLines", refundPolicy.lines());
        java.util.Map<Long, Integer> reembolso = new java.util.HashMap<>();
        for (ExperienceInstance instance : instances) {
            reembolso.put(instance.getId(), refundPolicy.percentFor(instance));
        }
        model.addAttribute("refundPercentByInstance", reembolso);
        var usuario = currentUser();
        model.addAttribute("esDueno", usuario != null && experience.getCreator() != null
                && usuario.getId().equals(experience.getCreator().getId()));
        // El organizador se muestra en la card del feed pero el detalle lo
        // omitía (auditoría UI). Se resuelve acá para no exponer el proxy lazy
        // en la vista ni cambiar la entidad.
        var creador = experience.getCreator();
        model.addAttribute("creatorName", creador == null ? null : creador.getUsername());
        model.addAttribute("creatorAvatar", creador == null ? null : creador.getAvatarBase64());
        return "experience/detail";
    }

    @GetMapping("/experiences/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Experience experience = experienceService.getOwned(id, currentUser());
        model.addAttribute("experienceForm", toForm(experience));
        model.addAttribute("experienceId", id);
        model.addAttribute("experienceCurrency",
                experience.getCurrencyCode() == null ? "MXN" : experience.getCurrencyCode());
        model.addAttribute("stops", experienceService.stopsOf(id));
        model.addAttribute("existingImages", imageRepository.findByExperienceIdOrderByIdAsc(id));
        return "experience/form";
    }

    @PostMapping("/experiences/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("experienceForm") ExperienceFormRequest request,
                         BindingResult bindingResult,
                         @RequestParam(required = false) String currencyCode,
                         @RequestParam(required = false, name = "stopPlace") List<String> stopPlace,
                         @RequestParam(required = false, name = "stopCity") List<String> stopCity,
                         @RequestParam(required = false, name = "stopCountry") List<String> stopCountry,
                          @RequestParam(required = false, name = "stopLat") List<Double> stopLat,
                          @RequestParam(required = false, name = "stopLng") List<Double> stopLng,
                          @RequestParam(required = false, name = "removeImageIds") List<Long> removeImageIds,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        model.addAttribute("experienceId", id);
        if (bindingResult.hasErrors()) {
            repopulateForm(model, currencyCode,
                    toStops(stopPlace, stopCity, stopCountry, stopLat, stopLng));
            return "experience/form";
        }
        try {
            experienceService.update(id, request, currentUser(),
                    currencyCode, toStops(stopPlace, stopCity, stopCountry, stopLat, stopLng));
            // Sin imágenes nuevas rige borrado selectivo; con nuevas ya hubo reemplazo total.
            if (request.images() == null || request.images().isEmpty()) {
                experienceService.removeImages(id, currentUser(), removeImageIds);
            }
            return "redirect:/experiences/" + id;
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            repopulateForm(model, currencyCode,
                    toStops(stopPlace, stopCity, stopCountry, stopLat, stopLng));
            return "experience/form";
        }
    }

    @PostMapping("/experiences/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        experienceService.delete(id, currentUser());
        redirectAttributes.addFlashAttribute("success", "Experiencia eliminada");
        return "redirect:/my-experiences";
    }

    /** "Tus Experiencias" (spec §6.3): lista de las experiencias propias. */
    @GetMapping("/my-experiences")
    public String mine(Model model) {
        User user = currentUser();
        List<Experience> experiencias = experienceService.mine(user.getId());
        model.addAttribute("experiencias", experiencias);
        // Todas las fotos por experiencia para el carousel de la lista.
        java.util.Map<Long, List<String>> covers = new java.util.HashMap<>();
        for (Experience e : experiencias) {
            List<String> todas = imageRepository.findByExperienceIdOrderByIdAsc(e.getId())
                    .stream().map(ExperienceImage::getData).toList();
            if (!todas.isEmpty()) covers.put(e.getId(), todas);
        }
        model.addAttribute("covers", covers);
        return "experience/my-experiences";
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new DomainException("Usuario no encontrado"));
    }

    private ExperienceFormRequest emptyForm() {
        return new ExperienceFormRequest("", "", 60, 1, 10, BigDecimal.ZERO,
                null, RecurrenceType.UNIQUE, 19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(null, null, LocalTime.of(10, 0),
                        LocalTime.of(12, 0), null, null)),
                List.of());
    }

    private ExperienceFormRequest toForm(Experience experience) {
        var schedules = experienceSchedules(experience);
        return new ExperienceFormRequest(
                experience.getName(),
                experience.getDescription(),
                experience.getDurationMinutes(),
                experience.getMinCapacity(),
                experience.getMaxCapacity(),
                experience.getPrice(),
                experience.getCategory(),
                experience.getRecurrenceType(),
                experience.getMeetingLat(),
                experience.getMeetingLng(),
                schedules,
                List.of());
    }

    private List<ScheduleEntryRequest> experienceSchedules(Experience experience) {
        return scheduleRepository.findByExperienceIdOrderByIdAsc(experience.getId()).stream()
                .map(s -> new ScheduleEntryRequest(s.getDate(), s.getDayOfWeek(),
                        s.getStartTime(), s.getEndTime(), s.getRangeStart(), s.getRangeEnd()))
                .toList();
    }

    /** Re-display tras error: conserva paradas y moneda para no borrar lo cargado. */
    private void repopulateForm(Model model, String currencyCode, List<ExperienceStopDTO> stops) {
        model.addAttribute("stops", stops);
        model.addAttribute("experienceCurrency", currencyCode);
    }

    /** Convierte las listas paralelas del form en DTOs; tolera forms viejos sin paradas. */
    private List<ExperienceStopDTO> toStops(List<String> place, List<String> city,
                                            List<String> country, List<Double> lat, List<Double> lng) {
        if (place == null || place.isEmpty()) return List.of();
        List<ExperienceStopDTO> stops = new java.util.ArrayList<>();
        for (int i = 0; i < place.size(); i++) {
            String p = place.get(i);
            if (p == null || p.isBlank()) continue;
            double la = lat != null && i < lat.size() && lat.get(i) != null ? lat.get(i) : 0;
            double lo = lng != null && i < lng.size() && lng.get(i) != null ? lng.get(i) : 0;
            stops.add(new ExperienceStopDTO(i,
                    p.trim(),
                    city != null && i < city.size() ? city.get(i) : "",
                    country != null && i < country.size() ? country.get(i) : "",
                    la, lo));
        }
        return stops;
    }
}
