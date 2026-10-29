package com.openlab.mx.movo.proposal.dto;

import com.openlab.mx.movo.experience.entity.ExperienceCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bloque de contenido editable en la pantalla 3: las mismas constraints que
 * ExperienceFormRequest para name/description/category/includes/bring —
 * lo logístico no vive acá (spec §1).
 */
public record ProposalForm(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull ExperienceCategory category,
        @Size(max = 2000, message = "Qué incluye admite hasta 2000 caracteres") String includes,
        @Size(max = 2000, message = "Qué llevar admite hasta 2000 caracteres") String bring) {
}
