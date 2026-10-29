package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ExperienceStopDTO;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.ExperienceImage;
import com.openlab.mx.movo.experience.entity.ExperienceInstance;
import com.openlab.mx.movo.experience.entity.ExperienceSchedule;
import com.openlab.mx.movo.experience.entity.ExperienceStatus;
import com.openlab.mx.movo.experience.entity.ExperienceStop;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.entity.Subrubro;
import com.openlab.mx.movo.experience.repository.ExperienceImageRepository;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceScheduleRepository;
import com.openlab.mx.movo.experience.repository.ExperienceStopRepository;
import com.openlab.mx.movo.experience.repository.SubrubroRepository;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.shared.exception.NotOwnerException;
import com.openlab.mx.movo.shared.location.GeocodingService;
import com.openlab.mx.movo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneId;import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperienceService {

    /** Lado mayor máximo de las fotos guardadas (se normalizan a JPEG). */
    private static final int MAX_IMAGE_DIMENSION = 1600;

    private final ExperienceRepository experienceRepository;
    private final ExperienceImageRepository imageRepository;
    private final ExperienceScheduleRepository scheduleRepository;
    private final ExperienceStopRepository stopRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final SubrubroRepository subrubroRepository;
    private final InstanceGenerationService generationService;
    private final GeocodingService geocodingService;
    private final com.openlab.mx.movo.identity.service.RequireVerifiedIdentityService
            requireVerifiedIdentityService;

    /** País/ciudad vacíos en las paradas se guardan como "—" si ni el form ni Nominatim los dan. */
    private static final String PLACEHOLDER = "—";

    @Transactional
    public Experience create(ExperienceFormRequest request, User creator) {
        return create(request, creator, null, List.of());
    }

    @Transactional
    public Experience create(ExperienceFormRequest request, User creator,
                             String currencyCode, List<ExperienceStopDTO> stops) {
        // KYC (Didit): publicar una experiencia exige identidad verificada.
        requireVerifiedIdentityService.requireVerified(creator == null ? null : creator.getId());
        validate(request);
        if (request.images() == null || request.images().isEmpty()) {
            throw new DomainException("Debe incluir al menos una imagen");
        }
        Experience experience = experienceRepository.save(build(request, creator, currencyCode));
        saveImages(experience, request.images());
        saveStops(experience, stops);
        List<ExperienceSchedule> schedules = saveSchedules(experience, request.schedules());
        instanceRepository.saveAll(
                generationService.generateFor(experience, schedules, LocalDate.now(ZoneId.systemDefault())));
        return experience;
    }

    @Transactional
    public Experience update(Long id, ExperienceFormRequest request, User editor) {
        return update(id, request, editor, null, List.of());
    }

    @Transactional
    public Experience update(Long id, ExperienceFormRequest request, User editor,
                             String currencyCode, List<ExperienceStopDTO> stops) {
        Experience experience = owned(id, editor);
        validate(request);
        applyFields(experience, request, currencyCode);
        Experience saved = experienceRepository.save(experience);

        if (request.images() != null && !request.images().isEmpty()) {
            imageRepository.deleteByExperienceId(id);
            saveImages(saved, request.images());
        }
        stopRepository.deleteByExperienceId(id);
        saveStops(saved, stops);
        scheduleRepository.deleteByExperienceId(id);
        List<ExperienceSchedule> schedules = saveSchedules(saved, request.schedules());

        List<ExperienceInstance> existing = instanceRepository.findByExperienceIdOrderByDateAsc(id);
        instanceRepository.saveAll(
                generationService.regenerateFutures(saved, schedules, existing, LocalDate.now(ZoneId.systemDefault())));
        return saved;
    }

    /**
     * Asigna el subtipo elegido en la tarjeta de propuesta (viaja por la URL
     * hasta el POST de crear/actualizar). Sólo se acepta si el subrubro está
     * activo y su categoría coincide con la de la experiencia: si el usuario
     * cambió la categoría en este form, el subtipo viejo se limpia en vez de
     * quedar cruzado. Id inexistente o null → no hace nada (basura jamás tira
     * el submit).
     */
    @Transactional
    public void asignarSubrubro(Long experienceId, Long subrubroId, ExperienceCategory categoria) {
        if (subrubroId == null) {
            return;
        }
        Experience experience = experienceRepository.findById(experienceId).orElse(null);
        if (experience == null) {
            return;
        }
        Subrubro subrubro = subrubroRepository.findById(subrubroId)
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .orElse(null);
        if (subrubro == null) {
            return;
        }
        boolean coincide = categoria != null && subrubro.getCategoria().equals(categoria.name());
        experience.setSubrubro(coincide ? subrubro : null);
        experienceRepository.save(experience);
    }

    /**
     * Borra imágenes existentes marcadas en edición. Solo las de esta
     * experiencia (los ids ajenos se ignoran: anti-IDOR).
     */
    @Transactional
    public void removeImages(Long id, User editor, List<Long> imageIds) {
        Experience experience = owned(id, editor);
        if (imageIds == null || imageIds.isEmpty()) return;
        List<ExperienceImage> propias = imageRepository.findByExperienceIdOrderByIdAsc(
                experience.getId());
        List<Long> borrar = propias.stream()
                .map(ExperienceImage::getId)
                .filter(imageIds::contains)
                .toList();
        if (borrar.isEmpty()) return;
        if (propias.size() - borrar.size() < 1) {
            throw new DomainException("Debe conservar al menos una imagen");
        }
        imageRepository.deleteAllById(borrar);
    }

    /**
     * Borrado lógico: la experiencia pasa a CANCELLED y desaparece del feed
     * (spec §4, estado ACTIVE|CANCELLED; preserva reservas e historial).
     */
    @Transactional
    public void delete(Long id, User editor) {
        Experience experience = owned(id, editor);
        experience.setStatus(ExperienceStatus.CANCELLED);
        experienceRepository.save(experience);
    }

    public Experience getOwned(Long id, User editor) {
        return owned(id, editor);
    }

    /** Experiencias propias para "Tus Experiencias" (spec §6.3). */
    public java.util.List<Experience> mine(Long creatorId) {
        return experienceRepository.findByCreatorId(creatorId);
    }

    public Experience get(Long id) {
        return experienceRepository.findById(id)
                .orElseThrow(() -> new DomainException("Experiencia no encontrada"));
    }

    private Experience owned(Long id, User editor) {
        Experience experience = get(id);
        if (!experience.getCreator().getId().equals(editor.getId())) {
            throw new NotOwnerException();
        }
        return experience;
    }

    private void validate(ExperienceFormRequest request) {
        if (request.maxCapacity() < request.minCapacity()) {
            throw new DomainException("El cupo máximo debe ser mayor o igual al mínimo");
        }
        if (request.schedules() == null || request.schedules().isEmpty()) {
            throw new DomainException("Debe definir al menos un horario");
        }
        for (ScheduleEntryRequest entry : request.schedules()) {
            if (entry == null) continue;
            if (request.recurrenceType() == RecurrenceType.WEEKLY) {
                if (entry.dayOfWeek() == null || entry.startTime() == null
                        || entry.endTime() == null || entry.rangeStart() == null) {
                    throw new DomainException(
                            "En agenda semanal el día, los horarios y el inicio del rango son obligatorios");
                }
            } else if (entry.date() == null || entry.startTime() == null || entry.endTime() == null) {
                throw new DomainException("La fecha y los horarios son obligatorios");
            }
        }
        if (request.images() != null) {
            for (MultipartFile img : request.images()) {
                if (img == null || img.isEmpty()) continue;
                String contentType = img.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    throw new DomainException("Solo se permiten imágenes (JPG, PNG, WebP, HEIC)");
                }
            }
        }
    }

    private Experience build(ExperienceFormRequest request, User creator) {
        return build(request, creator, null);
    }

    private Experience build(ExperienceFormRequest request, User creator, String currencyCode) {
        Experience experience = Experience.builder()
                .creator(creator)
                .name(request.name())
                .description(request.description())
                .includes(normalizeLines(request.includes()))
                .bring(normalizeLines(request.bring()))
                .durationMinutes(request.durationMinutes())
                .minCapacity(request.minCapacity())
                .maxCapacity(request.maxCapacity())
                .price(request.price())
                .currencyCode(resolveCurrency(currencyCode, creator))
                .category(request.category())
                .recurrenceType(request.recurrenceType())
                .meetingLat(request.meetingLat())
                .meetingLng(request.meetingLng())
                .build();
        return experience;
    }

    private void applyFields(Experience experience, ExperienceFormRequest request) {
        applyFields(experience, request, null);
    }

    private void applyFields(Experience experience, ExperienceFormRequest request, String currencyCode) {
        experience.setName(request.name());
        experience.setDescription(request.description());
        experience.setDurationMinutes(request.durationMinutes());
        experience.setMinCapacity(request.minCapacity());
        experience.setMaxCapacity(request.maxCapacity());
        experience.setPrice(request.price());
        String resolved = resolveCurrency(currencyCode, experience.getCreator());
        if (resolved != null) experience.setCurrencyCode(resolved);
        experience.setCategory(request.category());
        experience.setRecurrenceType(request.recurrenceType());
        experience.setMeetingLat(request.meetingLat());
        experience.setMeetingLng(request.meetingLng());
        experience.setIncludes(normalizeLines(request.includes()));
        experience.setBring(normalizeLines(request.bring()));
    }

    /**
     * Un ítem por línea: normaliza CRLF, recorta cada línea y descarta las vacías.
     * Devuelve {@code null} si no queda nada, para que la card y el detalle omitan
     * el bloque en vez de renderizar un contenedor vacío.
     *
     * <p>Se normaliza al escribir y no al leer: así los dos renderers de la card
     * (Thymeleaf y el template literal de feed-infinite.js) reciben el mismo
     * string limpio y el HTML no arrastra {@code \r}.
     */
    static String normalizeLines(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String linea : raw.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String limpia = linea.trim();
            if (limpia.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(limpia);
        }
        return out.isEmpty() ? null : out.toString();
    }

    private String resolveCurrency(String currencyCode, User creator) {
        if (currencyCode != null && !currencyCode.isBlank()) {
            return com.openlab.mx.movo.user.service.UserService.normalizeCurrency(currencyCode);
        }
        if (creator != null && creator.getCurrencyCode() != null && !creator.getCurrencyCode().isBlank()) {
            return creator.getCurrencyCode();
        }
        return "MXN";
    }

    /**
     * Paradas opcionales: se ignoran filas vacías para no romper forms viejos.
     * Si el form no trae ciudad/país (el picker del mapa solo manda nombre +
     * coords), se resuelven con Nominatim best-effort para que el filtro por
     * país del feed tenga datos reales en vez del placeholder "—".
     */
    private void saveStops(Experience experience, List<ExperienceStopDTO> stops) {
        if (stops == null || stops.isEmpty()) return;
        List<ExperienceStop> entities = new ArrayList<>();
        int pos = 0;
        for (ExperienceStopDTO s : stops) {
            if (s == null || s.placeName() == null || s.placeName().isBlank()) continue;
            if (s.lat() == 0 && s.lng() == 0) continue;
            String city = cleanLocation(s.city());
            String country = cleanLocation(s.country());
            if ((city == null || country == null)) {
                GeocodingService.Place place = reverseGeocode(s.lat(), s.lng());
                if (place != null) {
                    if (city == null) city = cleanLocation(place.city());
                    if (country == null) country = cleanLocation(place.country());
                }
            }
            entities.add(ExperienceStop.builder()
                    .experience(experience)
                    .position(pos++)
                    .placeName(s.placeName().trim())
                    .city(city != null ? city : PLACEHOLDER)
                    .country(country != null ? country : PLACEHOLDER)
                    .lat(s.lat())
                    .lng(s.lng())
                    .build());
        }
        if (!entities.isEmpty()) stopRepository.saveAll(entities);
    }

    /** null = sin dato (vacío o placeholder "—"); si no, valor recortado. */
    private static String cleanLocation(String raw) {
        if (raw == null || raw.isBlank() || PLACEHOLDER.equals(raw.trim())) return null;
        return raw.trim();
    }

    /** Reverse geocoding best-effort: nunca frena la creación si Nominatim falla. */
    private GeocodingService.Place reverseGeocode(double lat, double lng) {
        try {
            return geocodingService.resolve(lat, lng);
        } catch (Exception _) {
            return null;
        }
    }

    public List<ExperienceStopDTO> stopsOf(Long experienceId) {
        return stopRepository.findByExperienceIdOrderByPositionAscIdAsc(experienceId).stream()
                .map(s -> new ExperienceStopDTO(s.getPosition(), s.getPlaceName(),
                        displayLocation(s.getCity()), displayLocation(s.getCountry()),
                        s.getLat(), s.getLng()))
                .toList();
    }

    /**
     * El "—" es un artefacto de almacenamiento (columnas no nulas), no un
     * dato para mostrar: en el DTO de vista se expone como vacío para que
     * los templates no tengan que conocer el placeholder.
     */
    private static String displayLocation(String stored) {
        String clean = cleanLocation(stored);
        return clean != null ? clean : "";
    }

    private void saveImages(Experience experience, List<MultipartFile> files) {
        List<ExperienceImage> images = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            try {
                byte[] normalized = com.openlab.mx.movo.shared.image.ImageNormalizer.normalize(
                        files.get(i).getBytes(), MAX_IMAGE_DIMENSION);
                images.add(ExperienceImage.builder()
                        .experience(experience)
                        .data(Base64.getEncoder().encodeToString(normalized))
                        .position(i)
                        .build());
            } catch (IOException _) {
                throw new DomainException("No se pudo leer la imagen: " + files.get(i).getOriginalFilename());
            }
        }
        imageRepository.saveAll(images);
    }

    private List<ExperienceSchedule> saveSchedules(Experience experience,
                                                   List<ScheduleEntryRequest> entries) {
        List<ExperienceSchedule> schedules = new ArrayList<>();
        for (ScheduleEntryRequest entry : entries) {
            schedules.add(ExperienceSchedule.builder()
                    .experience(experience)
                    .date(entry.date())
                    .dayOfWeek(entry.dayOfWeek())
                    .startTime(entry.startTime())
                    .endTime(entry.endTime())
                    .rangeStart(entry.rangeStart())
                    .rangeEnd(entry.rangeEnd())
                    .build());
        }
        return scheduleRepository.saveAll(schedules);
    }
}
