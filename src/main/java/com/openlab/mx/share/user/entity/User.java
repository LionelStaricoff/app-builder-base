package com.openlab.mx.share.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(columnDefinition = "TEXT")
    private String avatarBase64;

    private String city;

    private Double lat;

    private Double lng;

    /**
     * País que resuelve el reverse geocoding de la ubicación GPS (ej. "Colombia").
     * Va en un campo propio: {@link #country} es el país elegido en el registro y
     * alimenta la sugerencia de moneda, así que el GPS no lo pisa.
     */
    private String locationCountry;

    /** País seleccionado en el registro (ej. "México"). Opcional para cuentas viejas. */
    private String country;

    /** Código ISO de moneda (ej. "MXN"). Default MXN si es null. */
    @Builder.Default
    private String currencyCode = "MXN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    private int spPoints = 0;

    /**
     * {@code false} = todavía no pasó por el onboarding de intereses. Sin este
     * flag no se puede distinguir "eligió cero intereses" de "nunca vio el
     * paso": el usuario que saltea volvería a ser interceptado en cada visita
     * al feed.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean interestsOnboarded = false;

    /**
     * {@code false} = todavía no vio la pantalla de propuesta con IA (paso 3
     * del onboarding). Se muestra una vez: "Crear experiencia" y "Saltear"
     * lo ponen en true.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean proposalOnboarded = false;
}
