package com.openlab.mx.share.proposal.generator;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.proposal.dto.ExperienceProposal;
import com.openlab.mx.share.proposal.dto.InterestItem;
import com.openlab.mx.share.proposal.dto.ProposalRequest;

import java.util.Arrays;

/**
 * Generador determinista sin red: es lo que corre en local, tests y e2e
 * (spec §4.4 — sin API key el flujo sigue activo y testeable).
 *
 * <p>Misma entrada, misma salida: templates fijos en español, sin fecha ni
 * azar. El texto libre del usuario entra tal cual en la descripción (el
 * service lo trunca a 1000 chars antes de llegar acá).
 */
public class StubProposalGenerator implements ProposalGenerator {

    private static final int MAX_DESCRIPCION = 2000;

    @Override
    public ExperienceProposal generate(ProposalRequest request) {
        InterestItem primero = request.intereses().isEmpty() ? null : request.intereses().get(0);

        String nombre = primero == null
                ? "Experiencia nueva"
                : "Experiencia de " + primero.subrubroNombre();

        String base = primero == null
                ? "Una experiencia local para descubrir a tu ritmo."
                : "Recorrido por " + primero.subrubroNombre() + " (" + primero.rubroLabel() + ") a tu medida.";

        String texto = request.textoLibre() == null ? "" : request.textoLibre().trim();
        String descripcion = texto.isEmpty() ? base : base + " " + texto;
        if (descripcion.length() > MAX_DESCRIPCION) {
            descripcion = descripcion.substring(0, MAX_DESCRIPCION);
        }

        return new ExperienceProposal(
                nombre,
                descripcion,
                categoriaDe(primero),
                "Guía local\nMaterial incluido",
                "Ropa cómoda\nAgua");
    }

    /** Rubro del interés 1 → enum; sin interés (o etiqueta desconocida), neutral. */
    private static ExperienceCategory categoriaDe(InterestItem item) {
        if (item == null || item.rubroLabel() == null) {
            return ExperienceCategory.CULTURAL;
        }
        return Arrays.stream(ExperienceCategory.values())
                .filter(c -> c.getLabel().equalsIgnoreCase(item.rubroLabel()))
                .findFirst()
                .orElse(ExperienceCategory.CULTURAL);
    }
}
