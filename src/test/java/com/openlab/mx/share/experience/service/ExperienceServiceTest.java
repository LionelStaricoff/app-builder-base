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
import com.openlab.mx.share.experience.repository.ExperienceStopRepository;
import com.openlab.mx.share.identity.service.RequireVerifiedIdentityService;
import com.openlab.mx.share.shared.exception.NotOwnerException;
import com.openlab.mx.share.shared.exception.UnverifiedIdentityException;
import com.openlab.mx.share.shared.location.GeocodingService;
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
    private ExperienceStopRepository stopRepository;
    @Mock
    private GeocodingService geocodingService;
    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private InstanceGenerationService generationService;
    @Mock
    private RequireVerifiedIdentityService requireVerifiedIdentityService;

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
                images, null, null);
    }

    @Test
    void testCreateSinIdentidadVerificadaSeBloquea() {
        org.mockito.Mockito.doThrow(new UnverifiedIdentityException())
                .when(requireVerifiedIdentityService).requireVerified(2L);

        org.junit.jupiter.api.Assertions.assertThrows(UnverifiedIdentityException.class,
                () -> experienceService.create(request(List.of()), user(2L)));
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

    /**
     * Compresión de fotos de experiencias: una foto real grande se guarda
     * redimensionada (máx 1600px) y en JPEG, para no inflar la BD ni el feed.
     */
    @Test
    void testCreateNormalizaFotoGrandeAJpeg1600() throws Exception {
        when(experienceRepository.save(any(Experience.class))).thenAnswer(inv -> {
            Experience e = inv.getArgument(0);
            e.setId(11L);
            return e;
        });
        when(imageRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(generationService.generateFor(any(), anyList(), any())).thenReturn(List.of());

        java.awt.image.BufferedImage foto = new java.awt.image.BufferedImage(
                2000, 1500, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 1500; y++) {
            for (int x = 0; x < 2000; x++) {
                foto.setRGB(x, y, ((x * 255 / 2000) << 16) | ((y * 255 / 1500) << 8) | 128);
            }
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(foto, "png", bos);
        byte[] png = bos.toByteArray();

        var img = new MockMultipartFile("images", "grande.png", "image/png", png);
        experienceService.create(request(List.of(img)), user(1L));

        ArgumentCaptor<List<ExperienceImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(imageRepository).saveAll(captor.capture());
        List<ExperienceImage> saved = captor.getValue();
        assertEquals(1, saved.size());
        byte[] stored = java.util.Base64.getDecoder().decode(saved.get(0).getData());
        // Magia JPEG: FF D8 FF.
        assertEquals((byte) 0xFF, stored[0], "la foto debería guardarse en JPEG");
        assertEquals((byte) 0xD8, stored[1], "la foto debería guardarse en JPEG");
        java.awt.image.BufferedImage storedImg = javax.imageio.ImageIO.read(
                new java.io.ByteArrayInputStream(stored));
        assertNotNull(storedImg, "la foto debe seguir siendo una imagen legible");
        assertTrue(Math.max(storedImg.getWidth(), storedImg.getHeight()) <= 1600,
                "la foto debería estar redimensionada a máx 1600px");
    }

    /**
     * Los bloques "Qué incluye" / "Qué llevar" se guardan como un ítem por
     * línea. Normalizar al escribir evita que los dos renderers de la card
     * (Thymeleaf y el template literal del scroll infinito) tengan que hacerlo.
     */
    @Test
    void normalizeLinesLimpiaCrLfEspaciosYLineasVacias() {
        assertEquals("Entrada al museo\nGuía bilingüe",
                ExperienceService.normalizeLines("  Entrada al museo  \r\n\r\n Guía bilingüe \r\n"));
        assertEquals("Uno\nDos", ExperienceService.normalizeLines("Uno\rDos"));
        assertNull(ExperienceService.normalizeLines(null));
        assertNull(ExperienceService.normalizeLines("   \n\t\r\n "),
                "un bloque con solo blancos equivale a no tenerlo");
    }

    @Test
    void testCreatePersisteIncludesYBringNormalizados() {
        when(experienceRepository.save(any(Experience.class))).thenAnswer(inv -> {
            Experience e = inv.getArgument(0);
            e.setId(12L);
            return e;
        });
        when(imageRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(generationService.generateFor(any(), anyList(), any())).thenReturn(List.of());

        Experience created = experienceService.create(
                requestConContenido("  Entrada al museo \r\n\r\n Guía bilingüe  ", "   "), user(1L));

        assertEquals("Entrada al museo\nGuía bilingüe", created.getIncludes());
        assertNull(created.getBring(), "qué llevar en blanco se guarda como null");
    }

    private ExperienceFormRequest requestConContenido(String includes, String bring) {
        var image = new MockMultipartFile("images", "uno.png", "image/png", "aaa".getBytes());
        return new ExperienceFormRequest(
                "Tour nocturno", "Descripción", 120, 2, 8, BigDecimal.valueOf(190),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE, 19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.of(2026, 12, 1), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), includes, bring);
    }
}
