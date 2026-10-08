package com.openlab.mx.share.experience.service;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Feed con scroll infinito: pageSize = 1, orden ciudad actual → cercanía
 * → próxima fecha; proyección con solo la imagen tapa (spec §5.2).
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    public static final int PAGE_SIZE = 1;

    private record Entry(Experience experience, LocalDate nextDate) {}

    private final ExperienceRepository experienceRepository;
    private final ExperienceImageRepository imageRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final FavoriteRepository favoriteRepository;

    public Page<ExperienceFeedDTO> page(Long userId, String userCity, Double lat, Double lng,
                                        ExperienceCategory category, int pageNumber) {
        List<Experience> experiences = category != null
                ? experienceRepository.findByStatusAndCategory(ExperienceStatus.ACTIVE, category)
                : experienceRepository.findByStatus(ExperienceStatus.ACTIVE);

        LocalDate today = LocalDate.now();
        List<Entry> entries = experiences.stream()
                .map(e -> new Entry(e, nextDateOf(e, today)))
                .sorted(comparator(userCity, lat, lng))
                .toList();

        int from = pageNumber * PAGE_SIZE;
        if (from >= entries.size()) {
            return new PageImpl<>(List.of(),
                    PageRequest.of(Math.max(pageNumber, 0), PAGE_SIZE), entries.size());
        }
        int to = Math.min(from + PAGE_SIZE, entries.size());
        List<ExperienceFeedDTO> content = entries.subList(from, to).stream()
                .map(entry -> toDto(entry.experience(), entry.nextDate(), userId))
                .toList();
        return new PageImpl<>(content, PageRequest.of(Math.max(pageNumber, 0), PAGE_SIZE),
                entries.size());
    }

    private Comparator<Entry> comparator(String userCity, Double lat, Double lng) {
        return Comparator
                .comparingInt((Entry entry) -> bucket(entry.experience(), userCity))
                .thenComparingDouble(entry -> distance(lat, lng, entry.experience()))
                .thenComparing(Entry::nextDate, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** Referencia a experiencia para otras capas (favoritos, etc.). */
    public Experience experienceRef(Long experienceId) {
        return experienceRepository.findById(experienceId)
                .orElseThrow(() -> new com.openlab.mx.share.shared.exception.DomainException(
                        "Experiencia no encontrada"));
    }

    /** DTO público con próxima fecha calculada (usado por favoritos). */
    public ExperienceFeedDTO dtoFor(Experience experience, Long userId) {
        return toDto(experience, nextDateOf(experience, LocalDate.now()), userId);
    }

    private int bucket(Experience experience, String userCity) {
        String creatorCity = experience.getCreator() != null ? experience.getCreator().getCity() : null;
        if (userCity == null || creatorCity == null) {
            return 1;
        }
        return userCity.equalsIgnoreCase(creatorCity) ? 0 : 1;
    }

    private double distance(Double lat, Double lng, Experience experience) {
        if (lat == null || lng == null) {
            return 0.0;
        }
        return Math.hypot(lat - experience.getMeetingLat(), lng - experience.getMeetingLng());
    }

    private LocalDate nextDateOf(Experience experience, LocalDate today) {
        return instanceRepository
                .findFirstByExperienceIdAndDateGreaterThanEqualAndStatusOrderByDateAsc(
                        experience.getId(), today, InstanceStatus.SCHEDULED)
                .map(i -> i.getDate())
                .orElse(null);
    }

    private ExperienceFeedDTO toDto(Experience experience, LocalDate nextDate, Long userId) {
        List<String> images = imageRepository
                .findByExperienceIdOrderByIdAsc(experience.getId()).stream()
                .map(img -> img.getData())
                .toList();
        String cover = images.isEmpty() ? null : images.get(0);
        boolean favorite = userId != null
                && favoriteRepository.existsByExperienceIdAndUserId(experience.getId(), userId);
        String label = String.format(Locale.US, "%.4f, %.4f",
                experience.getMeetingLat(), experience.getMeetingLng());
        return new ExperienceFeedDTO(
                experience.getId(),
                experience.getName(),
                experience.getDescription(),
                cover,
                images,
                label,
                experience.getDurationMinutes(),
                nextDate,
                experience.getPrice(),
                experience.getRatingAverage(),
                favorite
        );
    }
}
