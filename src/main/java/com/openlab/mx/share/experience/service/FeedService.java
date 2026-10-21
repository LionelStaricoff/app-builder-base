package com.openlab.mx.share.experience.service;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.dto.ExperienceStopDTO;
import com.openlab.mx.share.experience.dto.FirstStopCountry;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.UserViewedExperience;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.repository.ExperienceStopRepository;
import com.openlab.mx.share.experience.repository.UserViewedExperienceRepository;
import com.openlab.mx.share.reservation.entity.ReservationStatus;
import com.openlab.mx.share.reservation.repository.ReservationRepository;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
import com.openlab.mx.share.shared.JacksonHelper;
import com.openlab.mx.share.shared.location.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.ZoneId;import java.time.LocalDate;
import java.time.ZoneId;import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feed con scroll infinito: pageSize = 1, orden ciudad actual → cercanía
 * → próxima fecha; proyección con solo la imagen tapa (spec §5.2).
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    public static final int PAGE_SIZE = 1;

    /** País vacío en las paradas se guarda como "—" (ver ExperienceService.saveStops). */
    private static final String PLACEHOLDER = "—";

    private record Entry(Experience experience, LocalDate nextDate) {}

    private final ExperienceRepository experienceRepository;
    private final ExperienceImageRepository imageRepository;
    private final ExperienceStopRepository stopRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserViewedExperienceRepository viewedExperienceRepository;
    private final JacksonHelper jacksonHelper;
    private final GeocodingService geocodingService;

    /**
     * Lugar geocodificado del punto de reunión por experiencia.
     * Cache en memoria: el filtro solo geocodifica una vez por experiencia y
     * no repite llamadas a Nominatim en cada página del scroll infinito.
     */
    private final Map<Long, GeocodingService.Place> geocodedPlaceCache = new ConcurrentHashMap<>();

    /** Firma original: sin buscador libre (delega con cityQuery = null). */
    public Page<ExperienceFeedDTO> page(Long userId, String userCity, Double lat, Double lng,
                                        ExperienceCategory category, String country, int pageNumber) {
        return page(userId, userCity, lat, lng, category, country, null, pageNumber);
    }

    /**
     * Buscador libre {@code cityQuery}: filtra por país y/o ciudad (contiene,
     * sin distinguir mayúsculas) contra cualquier parada, la ciudad del
     * creador o el punto de reunión geocodificado. Se combina (AND) con el
     * filtro exacto {@code country} del dropdown.
     */
    public Page<ExperienceFeedDTO> page(Long userId, String userCity, Double lat, Double lng,
                                        ExperienceCategory category, String country,
                                        String cityQuery, int pageNumber) {
        List<Experience> experiences = category != null
                ? experienceRepository.findByStatusAndCategory(ExperienceStatus.ACTIVE, category)
                : experienceRepository.findByStatus(ExperienceStatus.ACTIVE);
        if (country != null && !country.isBlank()) {
            String wanted = country.trim();
            Map<Long, String> byExperience = firstStopCountriesById();
            experiences = experiences.stream()
                    .filter(e -> wanted.equalsIgnoreCase(countryOf(e, byExperience)))
                    .toList();
        }
        if (cityQuery != null && !cityQuery.isBlank()) {
            String wanted = cityQuery.trim();
            Map<Long, List<com.openlab.mx.share.experience.entity.ExperienceStop>> stopsByExperience =
                    allActiveStopsById();
            experiences = experiences.stream()
                    .filter(e -> matchesLocationQuery(e, wanted, stopsByExperience))
                    .toList();
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // Cargar experiencias ya vistas por este usuario (si userId es null, no hay vistas previas)
        Set<Long> viewedExperienceIds = Set.of();
        if (userId != null) {
            viewedExperienceRepository.findExperienceIdsByUserId(userId).forEach(viewedExperienceIds::add);
        }

        // Separar experiencias vistas y no vistas
        List<Entry> noViewed = new java.util.ArrayList<>();
        List<Entry> viewed = new java.util.ArrayList<>();
        for (Experience exp : experiences) {
            boolean isViewed = viewedExperienceIds.contains(exp.getId());
            Entry entry = new Entry(exp, nextDateOf(exp, today));
            if (isViewed) {
                viewed.add(entry);
            } else {
                noViewed.add(entry);
            }
        }

        // Ordenar las no vistas con el comparador normal, y las vistas van al final ordenadas por fecha
        noViewed = noViewed.stream()
                .sorted(comparator(userCity, lat, lng))
                .toList();
        viewed = viewed.stream()
                .sorted((a, b) -> b.nextDate().compareTo(a.nextDate()))
                .toList();

        List<Entry> entries = new java.util.ArrayList<>();
        entries.addAll(noViewed);
        entries.addAll(viewed);

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

    /**
     * Sugerencias del buscador de países del feed: países distintos de la
     * primera parada de las experiencias activas, sin repetidos por casing.
     */
    public List<String> countryOptions() {
        Map<String, String> porClave = new LinkedHashMap<>(); // minúscula → primer spelling visto
        for (FirstStopCountry row : stopRepository.findFirstStopCountries(ExperienceStatus.ACTIVE)) {
            String c = row.country() == null ? "" : row.country().trim();
            if (c.isBlank() || PLACEHOLDER.equals(c)) continue;
            porClave.putIfAbsent(c.toLowerCase(Locale.ROOT), c);
        }
        return porClave.values().stream()
                .sorted(Comparator.comparing(c -> c.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** Mapa experiencia → país de la primera parada, en una sola query. */
    private Map<Long, String> firstStopCountriesById() {
        Map<Long, String> byExperience = new HashMap<>();
        for (FirstStopCountry row : stopRepository.findFirstStopCountries(ExperienceStatus.ACTIVE)) {
            if (row.experienceId() != null && row.country() != null) {
                byExperience.put(row.experienceId(), row.country().trim());
            }
        }
        return byExperience;
    }

    /** Mapa experiencia → todas sus paradas, en una sola query (buscador libre). */
    private Map<Long, List<com.openlab.mx.share.experience.entity.ExperienceStop>> allActiveStopsById() {
        Map<Long, List<com.openlab.mx.share.experience.entity.ExperienceStop>> byExperience = new HashMap<>();
        List<com.openlab.mx.share.experience.entity.ExperienceStop> stops;
        try {
            stops = stopRepository.findAllActive(ExperienceStatus.ACTIVE);
        } catch (Exception _) {
            // Tabla aún no creada en H2 viejo: sin paradas no se filtra nada.
            return byExperience;
        }
        for (com.openlab.mx.share.experience.entity.ExperienceStop s : stops) {
            if (s.getExperience() == null || s.getExperience().getId() == null) continue;
            byExperience.computeIfAbsent(s.getExperience().getId(), k -> new java.util.ArrayList<>()).add(s);
        }
        return byExperience;
    }

    /**
     * ¿La experiencia matchea el buscador libre? Contiene (case-insensitive)
     * en ciudad/país/lugar de cualquier parada, ciudad del creador o lugar
     * geocodificado del punto de reunión (cubre datos viejos con "—").
     */
    private boolean matchesLocationQuery(Experience experience, String query,
                                         Map<Long, List<com.openlab.mx.share.experience.entity.ExperienceStop>> stopsByExperience) {
        String want = query.toLowerCase(Locale.ROOT);
        String creatorCity = experience.getCreator() != null ? experience.getCreator().getCity() : null;
        if (containsLocation(creatorCity, want)) return true;
        List<com.openlab.mx.share.experience.entity.ExperienceStop> stops =
                stopsByExperience.get(experience.getId());
        if (stops != null) {
            for (com.openlab.mx.share.experience.entity.ExperienceStop s : stops) {
                if (containsLocation(s.getCity(), want)
                        || containsLocation(s.getCountry(), want)
                        || containsLocation(s.getPlaceName(), want)) {
                    return true;
                }
            }
        }
        GeocodingService.Place place = geocodedPlace(experience);
        return place != null
                && (containsLocation(place.city(), want) || containsLocation(place.country(), want));
    }

    private static boolean containsLocation(String value, String wantLower) {
        if (value == null || value.isBlank() || PLACEHOLDER.equals(value.trim())) return false;
        return value.toLowerCase(Locale.ROOT).contains(wantLower);
    }

    /**
     * País de una experiencia para el filtro: la primera parada manda; si no
     * tiene, se reverse-geocodifica el punto de reunión (best effort, cacheado).
     */
    private String countryOf(Experience experience, Map<Long, String> byExperience) {
        String c = byExperience.get(experience.getId());
        if (c != null && !c.isBlank() && !PLACEHOLDER.equals(c)) {
            return c;
        }
        GeocodingService.Place place = geocodedPlace(experience);
        if (place == null || place.country() == null || place.country().isBlank()) {
            return "";
        }
        return place.country().trim();
    }

    /** Lugar geocodificado del punto de reunión (best effort, cacheado). */
    private GeocodingService.Place geocodedPlace(Experience experience) {
        if (experience.getMeetingLat() == null || experience.getMeetingLng() == null) {
            return null;
        }
        if (experience.getId() == null) {
            return geocodingService.resolve(experience.getMeetingLat(), experience.getMeetingLng());
        }
        // ConcurrentHashMap no admite nulls: el marcador NULLO representa "sin dato".
        GeocodingService.Place place = geocodedPlaceCache.computeIfAbsent(experience.getId(), id -> {
            GeocodingService.Place resolved = geocodingService.resolve(
                    experience.getMeetingLat(), experience.getMeetingLng());
            return resolved != null ? resolved : NULLO;
        });
        return place == NULLO ? null : place;
    }

    private static final GeocodingService.Place NULLO = new GeocodingService.Place(null, null);

    /** Referencia a experiencia para otras capas (favoritos, etc.). */
    public Experience experienceRef(Long experienceId) {
        return experienceRepository.findById(experienceId)
                .orElseThrow(() -> new com.openlab.mx.share.shared.exception.DomainException(
                        "Experiencia no encontrada"));
    }

    /** DTO público con próxima fecha calculada (usado por favoritos). */
    public ExperienceFeedDTO dtoFor(Experience experience, Long userId) {
        return toDto(experience, nextDateOf(experience, LocalDate.now(ZoneId.systemDefault())), userId);
    }

    /** Marca una experiencia como vista para el usuario actual. */
    public void markAsViewed(Long userId, Long experienceId) {
        if (userId == null || experienceId == null) return;
        UserViewedExperience viewed = UserViewedExperience.builder()
                .userId(userId)
                .experienceId(experienceId)
                .viewedAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build();
        viewedExperienceRepository.save(viewed);
    }

    private int bucket(Experience experience, String userCity) {
        String creatorCity = experience.getCreator() != null ? experience.getCreator().getCity() : null;
        if (userCity == null || creatorCity == null) {
            return 1;
        }
        return userCity.equalsIgnoreCase(creatorCity) ? 0 : 1;
    }

    /** Distancia Haversine en km (la hipotenusa de grados no es comparable entre lat/lng). */
    private double distance(Double lat, Double lng, Experience experience) {
        if (lat == null || lng == null || experience.getMeetingLat() == null
                || experience.getMeetingLng() == null) {
            return Double.MAX_VALUE; // sin coordenadas: al final, no primero
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(experience.getMeetingLat() - lat);
        double dLng = Math.toRadians(experience.getMeetingLng() - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(experience.getMeetingLat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
        List<ExperienceStopDTO> stops = List.of();
        try {
            stops = stopRepository.findByExperienceIdOrderByPositionAscIdAsc(experience.getId()).stream()
                    .map(s -> new ExperienceStopDTO(s.getPosition(), s.getPlaceName(),
                            s.getCity(), s.getCountry(), s.getLat(), s.getLng()))
                    .toList();
            if (!stops.isEmpty()) {
                var first = stops.get(0);
                label = first.placeName() + " · " + first.city();
            }
        } catch (Exception _) {
            // Tabla aún no creada en H2 viejo (ddl-auto=update la crea al arrancar).
        }
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
                favorite,
                experience.getCurrencyCode() == null ? "MXN" : experience.getCurrencyCode(),
                stops,
                jacksonHelper.toJson(stops),
                experience.getCreator() == null ? null : experience.getCreator().getUsername(),
                experience.getCreator() == null ? null : experience.getCreator().getAvatarBase64(),
                experience.getMaxCapacity(),
                availableCapacityFor(experience),
                reservedCountFor(experience),
                reservedAvatarsFor(experience)
        );
    }

    /** Próxima instancia reservable de la experiencia (la misma que nextDate). */
    private com.openlab.mx.share.experience.entity.ExperienceInstance nextInstance(Experience experience) {
        try {
            return instanceRepository
                    .findFirstByExperienceIdAndDateGreaterThanEqualAndStatusOrderByDateAsc(
                            experience.getId(), java.time.LocalDate.now(java.time.ZoneId.systemDefault()),
                            InstanceStatus.SCHEDULED)
                    .orElse(null);
        } catch (Exception _) {
            return null;
        }
    }

    private int reservedCountFor(Experience experience) {
        var instance = nextInstance(experience);
        if (instance == null || instance.getId() == null) return 0;
        try {
            return (int) reservationRepository.countAttendees(instance.getId());
        } catch (Exception _) {
            return 0;
        }
    }

    private int availableCapacityFor(Experience experience) {
        int reserved = reservedCountFor(experience);
        int max = experience.getMaxCapacity();
        return Math.max(0, max - reserved);
    }

    private List<String> reservedAvatarsFor(Experience experience) {
        var instance = nextInstance(experience);
        if (instance == null || instance.getId() == null) return List.of();
        try {
            var reservations = reservationRepository.findByInstanceIdAndStatusNot(instance.getId(),
                            ReservationStatus.CANCELADO);
            if (reservations == null) return List.of();
            return reservations.stream()
                    .map(r -> r.getUser() == null ? null : r.getUser().getAvatarBase64())
                    .filter(a -> a != null && !a.isBlank())
                    .distinct()
                    .limit(experience.getMaxCapacity())
                    .toList();
        } catch (Exception _) {
            return List.of();
        }
    }
}
