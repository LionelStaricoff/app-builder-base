package com.openlab.mx.share.shared.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Resuelve la ciudad/país del usuario a partir de sus coordenadas GPS
 * (reverse geocoding) usando Nominatim de OpenStreetMap, el mismo
 * proveedor de tiles que ya usa el mapa de la app.
 *
 * La política de Nominatim pide identificar la app y limitar volumen;
 * se geolocaliza solo cuando el usuario guarda su ubicación, no en
 * cada render del feed.
 */
@Service
@Slf4j
public class GeocodingService {

    private static final String USER_AGENT = "Shared/1.0 (shared.mx)";

    private final RestClient restClient;

    public GeocodingService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    /** Ciudad legible para mostrar y comparar (bucket del feed). */
    public Place resolve(double lat, double lng) {
        try {
            NominatimResponse r = restClient.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lng)
                            .queryParam("format", "jsonv2")
                            .queryParam("zoom", 10) // nivel ciudad
                            .queryParam("accept-language", "es")
                            .build())
                    .retrieve()
                    .body(NominatimResponse.class);
            if (r == null || (isBlank(r.city()) && isBlank(r.town()) && isBlank(r.village()))) {
                return null;
            }
            String city = firstNotBlank(r.city(), r.town(), r.village(), r.county());
            return new Place(city, r.address() != null ? r.address().country() : null);
        } catch (Exception e) {
            log.warn("Reverse geocoding falló para {},{}: {}", lat, lng, e.getMessage());
            return null; // el usuario conserva sus coordenadas aunque no se resuelva la ciudad
        }
    }

    public record Place(String city, String country) {}

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNotBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimResponse(String city, String town, String village, String county, Address address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(String country) {}
}
