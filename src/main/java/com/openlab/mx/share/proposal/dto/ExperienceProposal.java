package com.openlab.mx.share.proposal.dto;

import com.openlab.mx.share.experience.entity.ExperienceCategory;

/**
 * Propuesta generada: espejo del bloque de contenido de ExperienceFormRequest
 * (name, description, category, includes, bring). La IA no propone nada
 * logístico: fecha, precio y cupos viven en el form de alta.
 */
public record ExperienceProposal(String name, String description,
                                 ExperienceCategory category, String includes, String bring) {
}
