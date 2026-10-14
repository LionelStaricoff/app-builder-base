package com.openlab.mx.share.shared.location;

import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
public class LocationController {

    private final GeocodingService geocodingService;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    public record LocationRequest(
            @NotNull @Min(-90) @Max(90) Double lat,
            @NotNull @Min(-180) @Max(180) Double lng) {}

    public record LocationResponse(String city, String country) {}

    @PostMapping("/location")
    @ResponseBody
    public LocationResponse save(@Validated @RequestBody LocationRequest request) {
        User user = currentUser.require();
        user.setLat(request.lat());
        user.setLng(request.lng());
        GeocodingService.Place place = geocodingService.resolve(request.lat(), request.lng());
        if (place != null && place.city() != null) {
            user.setCity(place.city());
        }
        userRepository.save(user);
        return new LocationResponse(user.getCity(), null);
    }
}
