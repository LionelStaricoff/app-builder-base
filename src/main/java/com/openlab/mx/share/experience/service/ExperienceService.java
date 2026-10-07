package com.openlab.mx.share.experience.service;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceImage;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.ExperienceSchedule;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.repository.ExperienceScheduleRepository;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final ExperienceImageRepository imageRepository;
    private final ExperienceScheduleRepository scheduleRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final InstanceGenerationService generationService;

    @Transactional
    public Experience create(ExperienceFormRequest request, User creator) {
        validate(request);
        if (request.images() == null || request.images().isEmpty()) {
            throw new DomainException("Debe incluir al menos una imagen");
        }
        Experience experience = experienceRepository.save(build(request, creator));
        saveImages(experience, request.images());
        List<ExperienceSchedule> schedules = saveSchedules(experience, request.schedules());
        instanceRepository.saveAll(
                generationService.generateFor(experience, schedules, LocalDate.now()));
        return experience;
    }

    @Transactional
    public Experience update(Long id, ExperienceFormRequest request, User editor) {
        Experience experience = owned(id, editor);
        validate(request);
        applyFields(experience, request);
        Experience saved = experienceRepository.save(experience);

        if (request.images() != null && !request.images().isEmpty()) {
            imageRepository.deleteByExperienceId(id);
            saveImages(saved, request.images());
        }
        scheduleRepository.deleteByExperienceId(id);
        List<ExperienceSchedule> schedules = saveSchedules(saved, request.schedules());

        List<ExperienceInstance> existing = instanceRepository.findByExperienceIdOrderByDateAsc(id);
        instanceRepository.saveAll(
                generationService.regenerateFutures(saved, schedules, existing, LocalDate.now()));
        return saved;
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
    }

    private Experience build(ExperienceFormRequest request, User creator) {
        Experience experience = Experience.builder()
                .creator(creator)
                .name(request.name())
                .description(request.description())
                .durationMinutes(request.durationMinutes())
                .minCapacity(request.minCapacity())
                .maxCapacity(request.maxCapacity())
                .price(request.price())
                .category(request.category())
                .recurrenceType(request.recurrenceType())
                .meetingLat(request.meetingLat())
                .meetingLng(request.meetingLng())
                .build();
        return experience;
    }

    private void applyFields(Experience experience, ExperienceFormRequest request) {
        experience.setName(request.name());
        experience.setDescription(request.description());
        experience.setDurationMinutes(request.durationMinutes());
        experience.setMinCapacity(request.minCapacity());
        experience.setMaxCapacity(request.maxCapacity());
        experience.setPrice(request.price());
        experience.setCategory(request.category());
        experience.setRecurrenceType(request.recurrenceType());
        experience.setMeetingLat(request.meetingLat());
        experience.setMeetingLng(request.meetingLng());
    }

    private void saveImages(Experience experience, List<MultipartFile> files) {
        List<ExperienceImage> images = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            try {
                images.add(ExperienceImage.builder()
                        .experience(experience)
                        .data(Base64.getEncoder().encodeToString(files.get(i).getBytes()))
                        .position(i)
                        .build());
            } catch (IOException e) {
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
