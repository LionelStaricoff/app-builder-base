package com.openlab.mx.share.shared.location;

import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Ubicación del usuario: el navegador le pide permiso GPS una vez, manda
 * las coordenadas acá, se resuelve la ciudad con reverse geocoding y se
 * persiste en el perfil. El feed usa esos datos para priorizar lo cercano.
 */
@Controller
@Validated
@RequiredArgsConstructor
@Slf4j
public class LocationController {

    private final GeocodingService geocodingService;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    public record LocationRequest(
            @NotNull @Min(-90) @Max(90) Double lat,
            @NotNull @Min(-180) @Max(180) Double lng) {}

    public record LocationResponse(String city, String country, Double lat, Double lng) {}

    @PostMapping("/location")
    @ResponseBody
    public LocationResponse save(@Validated @RequestBody LocationRequest request) {
        long t0 = System.currentTimeMillis();
        System.out.println("[LOCATION] POST /location recibido lat=" + request.lat()
                + " lng=" + request.lng());
        log.info("[LOCATION] POST /location recibido lat={} lng={}", request.lat(), request.lng());
        User user = currentUser.require();
        System.out.println("[LOCATION] usuario=" + user.getUsername() + " cityAnterior=" + user.getCity()
                + " locationCountryAnterior=" + user.getLocationCountry());
        log.info("[LOCATION] usuario={} cityAnterior={} locationCountryAnterior={}",
                user.getUsername(), user.getCity(), user.getLocationCountry());

        // Coordenadas: siempre, son el dato duro que manda el GPS.
        user.setLat(request.lat());
        user.setLng(request.lng());

        // Ciudad y país: solo si el reverse geocoding los resolvió.
        GeocodingService.Place place = geocodingService.resolvePlace(request.lat(), request.lng());
        long tGeo = System.currentTimeMillis() - t0;
        System.out.println("[LOCATION] geocoding devolvió " + place + " en " + tGeo + "ms"
                + (place == null ? " (NADA RESUELTO)" : ""));
        log.info("[LOCATION] geocoding={} en {}ms", place, tGeo);

        if (place != null) {
            if (place.city() != null && !place.city().isBlank()) {
                user.setCity(place.city());
            }
            if (place.country() != null && !place.country().isBlank()) {
                user.setLocationCountry(place.country());
            }
        }
        userRepository.save(user);
        long total = System.currentTimeMillis() - t0;
        System.out.println("[LOCATION] GUARDADO user=" + user.getUsername()
                + " lat=" + user.getLat() + " lng=" + user.getLng()
                + " city=" + user.getCity() + " locationCountry=" + user.getLocationCountry()
                + " total=" + total + "ms");
        log.info("[LOCATION] GUARDADO user={} lat={} lng={} city={} locationCountry={} total={}ms",
                user.getUsername(), user.getLat(), user.getLng(), user.getCity(),
                user.getLocationCountry(), total);
        return new LocationResponse(user.getCity(), user.getLocationCountry(),
                user.getLat(), user.getLng());
    }
}
