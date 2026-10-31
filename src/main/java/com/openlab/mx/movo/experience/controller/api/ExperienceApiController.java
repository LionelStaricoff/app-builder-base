package com.openlab.mx.movo.experience.controller.api;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceImage;
import com.openlab.mx.movo.experience.repository.ExperienceImageRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * API REST read-only de detalle de una experiencia para un front separado.
 * Reutiliza ExperienceService y carga las imágenes de forma explícita para
 * evitar problemas de lazy-loading en la serialización JSON.
 */
@RestController
@RequestMapping("/api/experiences")
public class ExperienceApiController {

    private final ExperienceService experienceService;
    private final ExperienceImageRepository imageRepository;

    public ExperienceApiController(ExperienceService experienceService,
                                  ExperienceImageRepository imageRepository) {
        this.experienceService = experienceService;
        this.imageRepository = imageRepository;
    }

    public record ExperienceDetailResponse(
            Long id,
            String name,
            String description,
            String includes,
            String bring,
            int durationMinutes,
            int minCapacity,
            int maxCapacity,
            BigDecimal price,
            String currencyCode,
            String category,
            double ratingAverage,
            Double meetingLat,
            Double meetingLng,
            String creatorName,
            List<String> images) {
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExperienceDetailResponse> detail(@PathVariable Long id) {
        Experience experience = experienceService.get(id);

        List<String> images = imageRepository.findByExperienceIdOrderByIdAsc(id).stream()
                .map(ExperienceImage::getData)
                .toList();

        ExperienceDetailResponse body = new ExperienceDetailResponse(
                experience.getId(),
                experience.getName(),
                experience.getDescription(),
                experience.getIncludes(),
                experience.getBring(),
                experience.getDurationMinutes(),
                experience.getMinCapacity(),
                experience.getMaxCapacity(),
                experience.getPrice(),
                experience.getCurrencyCode(),
                experience.getCategory() == null ? null : experience.getCategory().name(),
                experience.getRatingAverage(),
                experience.getMeetingLat(),
                experience.getMeetingLng(),
                experience.getCreator() == null ? null : experience.getCreator().getUsername(),
                images.isEmpty() ? new ArrayList<>() : images);

        return ResponseEntity.ok(body);
    }
}
