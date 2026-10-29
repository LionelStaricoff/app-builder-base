package com.openlab.mx.movo.experience.dto;

/**
 * Proyección ligera para el filtro por país del feed: país de la primera
 * parada (position = 0) de cada experiencia activa.
 */
public record FirstStopCountry(Long experienceId, String country) {
}
