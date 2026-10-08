package com.openlab.mx.share.experience.service;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.ExperienceImage;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.experience.entity.ExperienceStatus;
import com.openlab.mx.share.experience.entity.InstanceStatus;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceImageRepository;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceRepository;
import com.openlab.mx.share.favorite.repository.FavoriteRepository;
import com.openlab.mx.share.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private ExperienceRepository experienceRepository;
    @Mock
    private ExperienceImageRepository imageRepository;
    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private FavoriteRepository favoriteRepository;

    @InjectMocks
    private FeedService feedService;

    private Experience experience(Long id, String name, String creatorCity,
                                  double lat, double lng, ExperienceCategory category) {
        User creator = User.builder().id(99L).username("c").city(creatorCity).build();
        return Experience.builder()
                .id(id).name(name).creator(creator)
                .description("desc").durationMinutes(60)
                .minCapacity(2).maxCapacity(8)
                .price(BigDecimal.valueOf(190))
                .category(category)
                .recurrenceType(RecurrenceType.UNIQUE)
                .meetingLat(lat).meetingLng(lng)
                .status(ExperienceStatus.ACTIVE)
                .build();
    }

    private void stubNextDate(Experience e, LocalDate date) {
        when(instanceRepository.findFirstByExperienceIdAndDateGreaterThanEqualAndStatusOrderByDateAsc(
                org.mockito.ArgumentMatchers.eq(e.getId()), any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(InstanceStatus.SCHEDULED)))
                .thenReturn(Optional.of(ExperienceInstance.builder()
                        .experience(e).date(date)
                        .startTime(java.time.LocalTime.of(10, 0))
                        .endTime(java.time.LocalTime.of(12, 0))
                        .status(InstanceStatus.SCHEDULED).build()));
    }

    private void stubCover(Experience e, String base64) {
        when(imageRepository.findByExperienceIdOrderByIdAsc(e.getId()))
                .thenReturn(List.of(ExperienceImage.builder()
                        .experience(e).data(base64).position(0).build()));
    }

    @Test
    void testPageSizeEs1() {
        Experience a = experience(1L, "A", "CDMX", 19.43, -99.13, ExperienceCategory.CULTURAL);
        Experience b = experience(2L, "B", "CDMX", 19.44, -99.14, ExperienceCategory.CULTURAL);
        Experience c = experience(3L, "C", "CDMX", 19.45, -99.15, ExperienceCategory.CULTURAL);
        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE)).thenReturn(List.of(a, b, c));
        stubNextDate(a, LocalDate.of(2026, 12, 1));
        stubNextDate(b, LocalDate.of(2026, 12, 2));
        stubNextDate(c, LocalDate.of(2026, 12, 3));
        stubCover(a, "tapa-a");

        var page = feedService.page(null, "CDMX", 19.4326, -99.1332, null, 0);

        assertEquals(1, page.getSize(), "pageSize debe ser 1 (estilo Instagram)");
        assertEquals(1, page.getContent().size());
        assertEquals(3, page.getTotalElements());
    }

    @Test
    void testOrdenaCiudadActualPrimero() {
        // Cerca pero de otra ciudad vs lejos pero de la ciudad del usuario:
        // el bucket de ciudad manda sobre la distancia.
        Experience otherCityNear = experience(1L, "Cerca Otra Ciudad", "Guadalajara",
                19.4326, -99.1331, ExperienceCategory.CULTURAL);
        Experience myCityFar = experience(2L, "Lejos Mi Ciudad", "CDMX",
                19.1000, -99.5000, ExperienceCategory.CULTURAL);
        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(otherCityNear, myCityFar));
        stubNextDate(otherCityNear, LocalDate.of(2026, 12, 1));
        stubNextDate(myCityFar, LocalDate.of(2026, 12, 2));
        stubCover(otherCityNear, "x");
        stubCover(myCityFar, "y");

        var first = feedService.page(null, "CDMX", 19.4326, -99.1332, null, 0);
        var second = feedService.page(null, "CDMX", 19.4326, -99.1332, null, 1);

        assertEquals("Lejos Mi Ciudad", first.getContent().get(0).name(),
                "la ciudad actual va primero aunque este mas lejos");
        assertEquals("Cerca Otra Ciudad", second.getContent().get(0).name());
    }

    @Test
    void testFiltraPorCategoria() {
        feedService.page(null, "CDMX", 19.4, -99.1, ExperienceCategory.DEPORTIVA, 0);

        verify(experienceRepository).findByStatusAndCategory(ExperienceStatus.ACTIVE,
                ExperienceCategory.DEPORTIVA);
        verify(experienceRepository, never()).findByStatus(any());
    }

    @Test
    void testPaginaMasAllaDelFinalDevuelveVacia() {
        Experience a = experience(1L, "A", "CDMX", 19.43, -99.13, ExperienceCategory.CULTURAL);
        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE)).thenReturn(List.of(a));
        stubNextDate(a, LocalDate.of(2026, 12, 1));

        var page = feedService.page(null, "CDMX", 19.4326, -99.1332, null, 99);

        assertTrue(page.isEmpty());
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void testDtoContieneTodasLasImagenes() {
        Experience a = experience(1L, "A", "CDMX", 19.43, -99.13, ExperienceCategory.CULTURAL);
        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE)).thenReturn(List.of(a));
        stubNextDate(a, LocalDate.of(2026, 12, 1));
        when(imageRepository.findByExperienceIdOrderByIdAsc(1L)).thenReturn(List.of(
                ExperienceImage.builder().experience(a).data("FOTO-1").position(0).build(),
                ExperienceImage.builder().experience(a).data("FOTO-2").position(1).build()));

        var page = feedService.page(null, "CDMX", 19.4326, -99.1332, null, 0);

        ExperienceFeedDTO dto = page.getContent().get(0);
        assertEquals(List.of("FOTO-1", "FOTO-2"), dto.images());
        assertEquals("FOTO-1", dto.coverBase64());
        verify(imageRepository).findByExperienceIdOrderByIdAsc(1L);
        assertEquals("19.4300, -99.1300", dto.meetingPointLabel());
        assertNotNull(dto.nextDate());
    }
}
