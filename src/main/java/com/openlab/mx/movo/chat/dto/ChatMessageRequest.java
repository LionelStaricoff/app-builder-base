package com.openlab.mx.movo.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mensaje de chat por instancia (spec §5.6).
 */
public record ChatMessageRequest(
        @NotBlank @Size(max = 2000) String text
) {
}
