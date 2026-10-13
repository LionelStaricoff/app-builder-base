package com.openlab.mx.share.shared;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Expone serialización JSON a Thymeleaf (data-stops para Leaflet). */
@Component("jacksonHelper")
@RequiredArgsConstructor
public class JacksonHelper {
    private final ObjectMapper objectMapper;

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
