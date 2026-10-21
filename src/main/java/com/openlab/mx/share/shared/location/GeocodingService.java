package com.openlab.mx.share.shared.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Locale;
import java.util.Set;
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

    /** addresstype de Nominatim que garantiza que el nombre es una ciudad. */
    private static final Set<String> CITY_LIKE =
            Set.of("city", "town", "village", "borough", "municipality");

    private final RestClient restClient;

    public GeocodingService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    /** Ciudad legible para mostrar y comparar (bucket del feed). */
    public Place resolve(double lat, double lng) {
        Place place = resolvePlace(lat, lng);
        return place == null || isBlank(place.city()) ? null : place;
    }

    /**
     * Igual que {@link #resolve} pero devuelve lo que haya: ciudad y/o país.
     * Sirve cuando no hay ciudad (zona rural/mar) y igual interesa persistir
     * el país. Devuelve null solo si no vino nada utilizable o hubo excepción.
     */
    public Place resolvePlace(double lat, double lng) {
        long t0 = System.currentTimeMillis();
        log.info("[GEOCODING] reverse → nominatim lat={} lng={}", lat, lng);
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
            long ms = System.currentTimeMillis() - t0;
            if (r == null) {
                log.warn("[GEOCODING] SIN RESPUESTA en {}ms", ms);
                return null;
            }
            String city = cityOf(r);
            String country = r.address() != null ? r.address().country() : null;
            if (isBlank(city) && isBlank(country)) {
                log.warn("[GEOCODING] NADA RESUELTO en {}ms → address={} name={} addresstype={}",
                        ms, r.address(), r.name(), r.addresstype());
                return null;
            }
            if (isBlank(city)) {
                log.warn("[GEOCODING] SOLO PAÍS en {}ms → city=null country={}", ms, country);
            } else {
                log.info("[GEOCODING] OK en {}ms → city={} country={}", ms, city, country);
            }
            return new Place(city, country);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - t0;
            log.warn("[GEOCODING] FALLO en {}ms → {}: {}", ms,
                    e.getClass().getSimpleName(), e.getMessage());
            return null; // el usuario conserva sus coordenadas aunque no se resuelva la ciudad
        }
    }

    public record Place(String city, String country) {}

    /**
     * jsonv2 (format=jsonv2) mete los componentes de dirección DENTRO de
     * "address": city/town/village/county del raíz del JSON casi nunca vienen.
     * Leemos address primero y el raíz como respaldo.
     */
    private static String cityOf(NominatimResponse r) {
        Address a = r.address();
        String fromAddress = a == null ? null : firstNotBlank(
                a.city(), a.town(), a.village(), a.municipality(), a.suburb(), a.county());
        String city = firstNotBlank(fromAddress,
                r.city(), r.town(), r.village(), r.county());
        if (!isBlank(city)) {
            return city;
        }
        // Último recurso: el nombre propio del lugar si Nominatim lo clasifica
        // como ciudad/town/village (p. ej. "Cartagena de Indias").
        if (r.addresstype() == null) {
            return null;
        }
        return CITY_LIKE.contains(r.addresstype().toLowerCase(Locale.ROOT)) ? r.name() : null;
    }

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
    record NominatimResponse(String city, String town, String village, String county,
                             String name, String addresstype, Address address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(String city, String town, String village, String county,
                   String municipality, String suburb, String country) {}
}
