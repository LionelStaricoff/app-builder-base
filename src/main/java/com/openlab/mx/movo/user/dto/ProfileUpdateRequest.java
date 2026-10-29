package com.openlab.mx.movo.user.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Actualización de perfil (spec §6.5): nombre, país, moneda y avatar.
 * Campos opcionales: vacío/nulo significa "sin cambios" (semántica del form).
 */
public record ProfileUpdateRequest(
        @Size(max = 30, message = "El nombre de usuario no puede superar 30 caracteres") String username,
        @Size(max = 60) String country,
        @Size(max = 3) String currencyCode,
        MultipartFile avatar
) {
}
