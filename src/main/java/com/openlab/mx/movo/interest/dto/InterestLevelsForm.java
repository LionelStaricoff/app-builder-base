package com.openlab.mx.movo.interest.dto;

import com.openlab.mx.movo.interest.entity.InterestLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backing object del paso 2. Los radios viajan como {@code levels[<id>]}
 * (clave String a propósito: el binding de un Map con clave Long falla en
 * silencio si el contenedor decide otra conversión, y acá no queremos adivinar).
 */
public class InterestLevelsForm {

    private List<Long> subrubroIds = new ArrayList<>();

    private Map<String, InterestLevel> levels = new LinkedHashMap<>();

    public List<Long> getSubrubroIds() {
        return subrubroIds;
    }

    public void setSubrubroIds(List<Long> subrubroIds) {
        this.subrubroIds = subrubroIds == null ? new ArrayList<>() : subrubroIds;
    }

    public Map<String, InterestLevel> getLevels() {
        return levels;
    }

    public void setLevels(Map<String, InterestLevel> levels) {
        this.levels = levels == null ? new LinkedHashMap<>() : levels;
    }

    /** Claves inválidas se ignoran: el id ya se validó contra el catálogo. */
    public Map<Long, InterestLevel> nivelesPorSubrubroId() {
        Map<Long, InterestLevel> porId = new LinkedHashMap<>();
        levels.forEach((clave, nivel) -> {
            if (nivel == null) {
                return;
            }
            try {
                porId.put(Long.parseLong(clave), nivel);
            } catch (NumberFormatException ignored) {
                // clave basura: no es un id
            }
        });
        return porId;
    }
}
