package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceImage;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;
    private final UserRepository userRepository;
    private final ExperienceImageRepository imageRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final com.openlab.mx.share.experience.repository.ExperienceScheduleRepository scheduleRepository;

    @GetMapping("/experiences/new")
    public String form(Model model) {
        model.addAttribute("experienceForm", emptyForm());
        return "experience/form";
    }

    @PostMapping("/experiences")
    public String create(@Valid @ModelAttribute("experienceForm") ExperienceFormRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "experience/form";
        }
        try {
            Experience experience = experienceService.create(request, currentUser());
            return "redirect:/experiences/" + experience.getId();
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            return "experience/form";
        }
    }

    @GetMapping("/experiences/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Experience experience = experienceService.get(id);
        List<ExperienceImage> images = imageRepository.findByExperienceIdOrderByIdAsc(id);
        List<ExperienceInstance> instances = instanceRepository
                .findByExperienceIdAndDateGreaterThanEqualOrderByDateAsc(id, LocalDate.now()).stream()
                .filter(i -> i.getStatus() == InstanceStatus.SCHEDULED)
                .toList();
        model.addAttribute("experience", experience);
        model.addAttribute("images", images);
        model.addAttribute("instances", instances);
        return "experience/detail";
    }

    @GetMapping("/experiences/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Experience experience = experienceService.getOwned(id, currentUser());
        model.addAttribute("experienceForm", toForm(experience));
        model.addAttribute("experienceId", id);
        return "experience/form";
    }

    @PostMapping("/experiences/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("experienceForm") ExperienceFormRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        model.addAttribute("experienceId", id);
        if (bindingResult.hasErrors()) {
            return "experience/form";
        }
        try {
            experienceService.update(id, request, currentUser());
            return "redirect:/experiences/" + id;
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            return "experience/form";
        }
    }

    @PostMapping("/experiences/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        experienceService.delete(id, currentUser());
        redirectAttributes.addFlashAttribute("success", "Experiencia eliminada");
        return "redirect:/my-experiences";
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
}
