package com.openlab.mx.share.experience.service;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceImage;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.experience.repository.ExperienceScheduleRepository;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperienceServiceTest {

    @Mock
    private ExperienceRepository experienceRepository;
    @Mock
    private ExperienceImageRepository imageRepository;
    @Mock
    private ExperienceScheduleRepository scheduleRepository;
    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private InstanceGenerationService generationService;

    @InjectMocks
    private ExperienceService experienceService;

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private ExperienceFormRequest request(List<org.springframework.web.multipart.MultipartFile> images) {
        return new ExperienceFormRequest(
                "Tour nocturno",
                "Descripción",
                120,
                2,
                8,
                BigDecimal.valueOf(190),
                ExperienceCategory.CULTURAL,
                RecurrenceType.UNIQUE,
                19.4326,
                -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.of(2026, 12, 1), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                images);
    }

    @Test
    void testCreatePersisteImagenesPosition0EsTapa() {
        when(experienceRepository.save(any(Experience.class))).thenAnswer(inv -> {
            Experience e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(imageRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(generationService.generateFor(any(), anyList(), any())).thenReturn(List.of());

        var img1 = new MockMultipartFile("images", "uno.png", "image/png", "aaa".getBytes());
        var img2 = new MockMultipartFile("images", "dos.png", "image/png", "bbb".getBytes());

        Experience created = experienceService.create(request(List.of(img1, img2)), user(1L));

        assertEquals(10L, created.getId());
        ArgumentCaptor<List<ExperienceImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(imageRepository).saveAll(captor.capture());
        List<ExperienceImage> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(0, saved.get(0).getPosition(), "la primera es la tapa");
        assertEquals(1, saved.get(1).getPosition());
        assertNotNull(saved.get(0).getData());
    }

    @Test
    void testUpdateRechazaSiNoEsDueno() {
        Experience existente = Experience.builder().id(3L).creator(user(1L)).build();
        when(experienceRepository.findById(3L)).thenReturn(Optional.of(existente));

        assertThrows(NotOwnerException.class,
                () -> experienceService.update(3L, request(List.of()), user(2L)));
    }

    @Test
    void testDeleteRechazaSiNoEsDueno() {
        Experience existente = Experience.builder().id(3L).creator(user(1L)).build();
        when(experienceRepository.findById(3L)).thenReturn(Optional.of(existente));

        assertThrows(NotOwnerException.class,
                () -> experienceService.delete(3L, user(2L)));
    }
}
