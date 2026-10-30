package com.openlab.mx.movo.experience.service;

import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceImage;
import com.openlab.mx.movo.experience.entity.ExperienceStatus;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceImageRepository;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceRepository;
import com.openlab.mx.movo.experience.repository.ExperienceStopRepository;
import com.openlab.mx.movo.experience.repository.UserViewedExperienceRepository;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.shared.JacksonHelper;
import com.openlab.mx.movo.shared.location.GeocodingService;
import com.openlab.mx.movo.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Filtro de distancia del feed: el radio recorta la lista, no solo la ordena.
 *
 * <p>PAGE_SIZE es 1, así que "cuántas quedaron" se mide con
 * {@code getTotalElements()} y "cuál se muestra" con la primera card.
 */
@ExtendWith(MockitoExtension.class)
class FeedDistanceTest {

    /** demo_user del seeder: CDMX. */
    private static final double CDMX_LAT = 19.4326;
    private static final double CDMX_LNG = -99.1332;

    @Mock
    private ExperienceRepository experienceRepository;
    @Mock
    private ExperienceImageRepository imageRepository;
    @Mock
    private ExperienceInstanceRepository instanceRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ExperienceStopRepository stopRepository;
    @Mock
    private UserViewedExperienceRepository viewedExperienceRepository;
    @Mock
    private JacksonHelper jacksonHelper;
    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private FeedService feedService;

    private Experience experience(Long id, String name, Double lat, Double lng) {
        User creator = User.builder().id(99L).username("c").city("CDMX").build();
        return Experience.builder()
                .id(id).name(name).creator(creator)
                .description("desc").durationMinutes(60)
                .minCapacity(2).maxCapacity(8)
                .price(BigDecimal.valueOf(190))
                .recurrenceType(RecurrenceType.UNIQUE)
                .meetingLat(lat).meetingLng(lng)
                .status(ExperienceStatus.ACTIVE)
                .build();
    }

    /** Solo la card que se renderiza necesita tapa (PAGE_SIZE = 1). */
    private void stubCover(Experience e) {
        when(imageRepository.findByExperienceIdOrderByIdAsc(e.getId()))
                .thenReturn(List.of(ExperienceImage.builder()
                        .experience(e).data("tapa").position(0).build()));
    }

    @Test
    void radioDejaSoloLasQueQuedanAdentro() {
        Experience centro = experience(1L, "Centro", CDMX_LAT, CDMX_LNG);
        Experience coyoacan = experience(2L, "Coyoacán", 19.35, -99.16);   // ~9 km
        Experience madrid = experience(3L, "Madrid", 40.4168, -3.7038);    // ~9000 km

        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(madrid, coyoacan, centro));
        stubCover(centro);

        var page = feedService.page(null, "CDMX", CDMX_LAT, CDMX_LNG, null, null, null, 5, 0);

        assertEquals(1, page.getTotalElements(), "solo Centro está a menos de 5 km");
        assertEquals("Centro", page.getContent().get(0).name());
    }

    @Test
    void unRadioMasGrandeDejaEntrarLaQueEstabaJustoAfuera() {
        Experience centro = experience(1L, "Centro", CDMX_LAT, CDMX_LNG);
        Experience coyoacan = experience(2L, "Coyoacán", 19.35, -99.16);
        Experience madrid = experience(3L, "Madrid", 40.4168, -3.7038);

        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(madrid, coyoacan, centro));
        stubCover(centro);

        var page = feedService.page(null, "CDMX", CDMX_LAT, CDMX_LNG, null, null, null, 25, 0);

        assertEquals(2, page.getTotalElements(), "Centro (~0 km) y Coyoacán (~9 km) sí; Madrid no");
    }

    @Test
    void sinRadioNoSeFiltraNada() {
        Experience centro = experience(1L, "Centro", CDMX_LAT, CDMX_LNG);
        Experience madrid = experience(2L, "Madrid", 40.4168, -3.7038);

        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(madrid, centro));
        stubCover(centro);

        var page = feedService.page(null, "CDMX", CDMX_LAT, CDMX_LNG, null, null, null, null, 0);

        assertEquals(2, page.getTotalElements());
    }

    @Test
    void experienciaSinCoordenadasQuedaFueraDelRadio() {
        // De una experiencia sin punto de encuentro no se puede afirmar que
        // esté cerca: mostrarla con el chip "hasta 5 km" sería mentir.
        Experience sinPunto = experience(1L, "Sin punto", null, null);

        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(sinPunto));

        var page = feedService.page(null, "CDMX", CDMX_LAT, CDMX_LNG, null, null, null, 50, 0);

        assertEquals(0, page.getTotalElements());
    }

    @Test
    void laCardTraeLaDistanciaMedida() {
        Experience centro = experience(1L, "Centro", CDMX_LAT, CDMX_LNG);

        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(centro));
        stubCover(centro);

        var page = feedService.page(null, "CDMX", CDMX_LAT, CDMX_LNG, null, null, null, 5, 0);

        assertEquals(0.0, page.getContent().get(0).distanceKm(), 0.01);
    }

    @Test
    void sinUbicacionDelUsuarioLaCardNoMuestraDistancia() {
        Experience centro = experience(1L, "Centro", CDMX_LAT, CDMX_LNG);

        when(experienceRepository.findByStatus(ExperienceStatus.ACTIVE))
                .thenReturn(List.of(centro));
        stubCover(centro);

        var page = feedService.page(null, null, null, null, null, null, null, null, 0);

        assertNull(page.getContent().get(0).distanceKm(),
                "sin de dónde medir, la card no inventa kilómetros");
    }
}
