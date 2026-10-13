package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Feed: render inicial de la página 0 (Thymeleaf) + API paginada
 * pageSize=1 para el scroll infinito. El navegador puede mandar lat/lng
 * (GPS) que pisan los del perfil.
 */
@Controller
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final CurrentUser currentUser;

    @GetMapping("/feed")
    public String feed(@RequestParam(required = false) ExperienceCategory category,
                       @RequestParam(required = false) String city,
                       @RequestParam(required = false) String country,
                       Model model) {
        User user = currentUser.get();
        // Búsqueda manual de otro lugar (país y/o ciudad) pisa la ciudad del perfil.
        String effectiveCity = city != null && !city.isBlank() ? city.trim() : (user != null ? user.getCity() : null);
        Page<ExperienceFeedDTO> feedPage = feedService.page(
                user != null ? user.getId() : null,
                effectiveCity,
                user != null ? user.getLat() : null,
                user != null ? user.getLng() : null,
                category, country, city, 0);
        model.addAttribute("feedPage", feedPage);
        model.addAttribute("categories", ExperienceCategory.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedCountry", country);
        model.addAttribute("countries", feedService.countryOptions());
        model.addAttribute("searchedCity", effectiveCity);
        model.addAttribute("searchedQuery", city != null && !city.isBlank() ? city.trim() : null);
        model.addAttribute("savedCity", user != null ? user.getCity() : null);
        return "feed";
    }

    @GetMapping("/feed/api")
    @ResponseBody
    public Page<ExperienceFeedDTO> feedApi(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(required = false) ExperienceCategory category,
                                           @RequestParam(required = false) String country,
                                           @RequestParam(required = false) String city,
                                           @RequestParam(required = false) Double lat,
                                           @RequestParam(required = false) Double lng) {
        User user = currentUser.get();
        String effectiveCity = city != null && !city.isBlank() ? city.trim()
                : (user != null ? user.getCity() : null);
        return feedService.page(
                user != null ? user.getId() : null,
                effectiveCity,
                lat != null ? lat : (user != null ? user.getLat() : null),
                lng != null ? lng : (user != null ? user.getLng() : null),
                category, country, city, page);
    }
}
