package com.openlab.mx.share.interest.dto;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.Subrubro;

import java.util.List;

/**
 * Un rubro del catálogo con sus subrubros. {@code subrubros} puede estar vacío:
 * la UI muestra igual los cinco rubros para que la taxonomía no cambie de forma
 * según los datos.
 */
public record InterestRubro(ExperienceCategory rubro, String label, List<Subrubro> subrubros) {

    public int getCantidad() {
        return subrubros.size();
    }
}
