package com.openlab.mx.share.shared.location;

import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repro del bug reportado: el botón 📍 Activar hacía POST /location con
 * 200 pero nada se persistía (faltaba el save). Estos tests fijan que lo
 * posteado termina en los datos del usuario.
 */
@ExtendWith(MockitoExtension.class)
class LocationControllerTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private LocationController locationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(locationController).build();
    }

    private static User usuario() {
        return User.builder().id(7L).username("viajero").city("CDMX").build();
    }

    @Test
    void postGuardaUbicacionYResuelveCiudad() throws Exception {
        User user = usuario();
        when(currentUser.require()).thenReturn(user);
        when(geocodingService.resolvePlace(-12.0464, -77.0428))
                .thenReturn(new GeocodingService.Place("Lima", "Perú"));

        mockMvc.perform(post("/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":-12.0464,\"lng\":-77.0428}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Lima"))
                .andExpect(jsonPath("$.country").value("Perú"))
                .andExpect(jsonPath("$.lat").value(-12.0464))
                .andExpect(jsonPath("$.lng").value(-77.0428));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User guardado = captor.getValue();
        assertEquals(-12.0464, guardado.getLat(), 0.000001, "la latitud posteada se guarda");
        assertEquals(-77.0428, guardado.getLng(), 0.000001, "la longitud posteada se guarda");
        assertEquals("Lima", guardado.getCity(), "la ciudad geocodificada se guarda");
        assertEquals("Perú", guardado.getLocationCountry(), "el país geocodificado se guarda");
    }

    @Test
    void postSinGeocodingIgualGuardaCoordenadas() throws Exception {
        User user = usuario();
        when(currentUser.require()).thenReturn(user);
        when(geocodingService.resolvePlace(anyDouble(), anyDouble())).thenReturn(null);

        mockMvc.perform(post("/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":19.4326,\"lng\":-99.1332}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("CDMX"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User guardado = captor.getValue();
        assertEquals(19.4326, guardado.getLat(), 0.000001, "sin geocoding igual se guardan las coords");
        assertEquals(-99.1332, guardado.getLng(), 0.000001, "sin geocoding igual se guardan las coords");
        assertEquals("CDMX", guardado.getCity(), "sin geocoding se conserva la ciudad anterior");
    }

    /** Zona sin ciudad: Nominatim no devuelve city pero sí country (y las coords están). */
    @Test
    void postSoloPaisIgualSeGuardaPais() throws Exception {
        User user = usuario();
        when(currentUser.require()).thenReturn(user);
        when(geocodingService.resolvePlace(anyDouble(), anyDouble()))
                .thenReturn(new GeocodingService.Place(null, "Colombia"));

        mockMvc.perform(post("/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":10.3976,\"lng\":-75.4649}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("Colombia"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User guardado = captor.getValue();
        assertEquals("Colombia", guardado.getLocationCountry(), "el país se guarda sin ciudad");
        assertEquals("CDMX", guardado.getCity(), "sin ciudad se conserva la anterior");
    }
}
