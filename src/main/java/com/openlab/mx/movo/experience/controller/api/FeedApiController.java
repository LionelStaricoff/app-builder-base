package com.openlab.mx.movo.experience.controller.api;

import com.openlab.mx.movo.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.service.FeedService;
import com.openlab.mx.movo.shared.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * API REST read-only del feed (para un front separado).
 * Reutiliza FeedService / ExperienceFeedDTO sin alterar las vistas Thymeleaf.
 */
@RestController
@RequestMapping("/api/feed")
public class FeedApiController {

    private final FeedService feedService;
    private final CurrentUser currentUser;

    public FeedApiController(FeedService feedService, CurrentUser currentUser) {
        this.feedService = feedService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<Page<ExperienceFeedDTO>> feed(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "cityQuery", required = false) String cityQuery,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "maxDistanceKm", required = false) Integer maxDistanceKm) {

        ExperienceCategory parsedCategory = null;
        if (category != null && !category.isBlank()) {
            parsedCategory = ExperienceCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        }

        Long userId = (currentUser.get() != null) ? currentUser.get().getId() : null;
        Page<ExperienceFeedDTO> result = feedService.page(
                userId, null, lat, lng, parsedCategory, country, cityQuery, maxDistanceKm, Math.max(page, 0));

        return ResponseEntity.ok(result);
    }
}
