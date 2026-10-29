package com.openlab.mx.movo.interest.service;

import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.Subrubro;
import com.openlab.mx.movo.experience.repository.SubrubroRepository;
import com.openlab.mx.movo.interest.dto.InterestRubro;
import com.openlab.mx.movo.interest.entity.InterestLevel;
import com.openlab.mx.movo.interest.entity.UserInterest;
import com.openlab.mx.movo.interest.repository.UserInterestRepository;
import com.openlab.mx.movo.shared.exception.DomainException;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Catálogo de subrubros agrupado por rubro y las reglas para guardar los
 * intereses de un usuario.
 */
@Service
@RequiredArgsConstructor
public class InterestService {

    /** Rubro primero, después nombre: el orden de lectura de toda la feature. */
    private static final Comparator<Subrubro> ORDEN_CATALOGO = Comparator
            .comparingInt((Subrubro s) -> ordenCategoria(s.getCategoria()))
            .thenComparing(Subrubro::getNombre, String.CASE_INSENSITIVE_ORDER);

    /** Techo de intereses: con 21 subrubros en el catálogo, más que esto deja
     *  de ser una señal y el paso de niveles se vuelve una lista eterna. */
    public static final int MAX_INTERESES = 8;

    private static final Logger log = LoggerFactory.getLogger(InterestService.class);

    private final UserInterestRepository userInterestRepository;
    private final SubrubroRepository subrubroRepository;
    private final UserRepository userRepository;

    /**
     * Los cinco rubros en el orden del enum, cada uno con sus subrubros activos
     * ordenados por nombre. Un rubro sin subrubros aparece igual: la taxonomía
     * no cambia de forma según los datos.
     */
    public List<InterestRubro> catalogo() {
        List<Subrubro> activos = subrubroRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .toList();

        List<InterestRubro> rubros = new ArrayList<>();
        int clasificados = 0;
        for (ExperienceCategory rubro : ExperienceCategory.values()) {
            List<Subrubro> delRubro = activos.stream()
                    .filter(s -> rubro.name().equals(s.getCategoria()))
                    .sorted(Comparator.comparing(Subrubro::getNombre, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            clasificados += delRubro.size();
            rubros.add(new InterestRubro(rubro, rubro.getLabel(), delRubro));
        }

        int huerfanos = activos.size() - clasificados;
        if (huerfanos > 0) {
            // Sin FK, un string con tilde de más ("GASTRONOMÍA") no matchea el
            // enum y desaparecería en silencio: al menos queda el aviso.
            log.warn("{} subrubro(s) activos con categoria fuera de ExperienceCategory: no se muestran en el onboarding",
                    huerfanos);
        }
        return rubros;
    }

    public List<UserInterest> intereses(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        return userInterestRepository.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(UserInterest::getSubrubro, ORDEN_CATALOGO))
                .toList();
    }

    /**
     * Normaliza una colección arbitraria de subrubros al orden del catálogo.
     * El paso 2 lo usa porque el POST del paso 1 llega en el orden del DOM, no
     * en el orden en que el usuario eligió.
     */
    public List<Subrubro> ordenar(Collection<Subrubro> subrubros) {
        if (subrubros == null) {
            return List.of();
        }
        return subrubros.stream()
                .filter(Objects::nonNull)
                .sorted(ORDEN_CATALOGO)
                .toList();
    }

    public List<Long> idsSeleccionados(User user) {
        return intereses(user).stream()
                .map(i -> i.getSubrubro().getId())
                .toList();
    }

    public Map<Long, InterestLevel> niveles(User user) {
        return intereses(user).stream()
                .collect(Collectors.toMap(
                        i -> i.getSubrubro().getId(),
                        UserInterest::getLevel,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * Reemplaza el set de intereses del usuario. Lista vacía es válido y deja al
     * usuario sin intereses (equivale a saltear el paso).
     */
    @Transactional
    public void guardar(User user, List<Long> subrubroIds, Map<Long, InterestLevel> niveles) {
        if (user == null || user.getId() == null) {
            throw new DomainException("Usuario no encontrado");
        }

        List<Long> ids = subrubroIds == null ? List.of()
                : subrubroIds.stream().filter(Objects::nonNull).distinct().toList();

        if (ids.size() > MAX_INTERESES) {
            throw new DomainException("Podés elegir hasta " + MAX_INTERESES + " intereses");
        }

        List<Subrubro> elegidos = ids.isEmpty() ? List.of() : subrubroRepository.findAllById(ids);
        boolean invalido = elegidos.size() != ids.size()
                || elegidos.stream().anyMatch(s -> !Boolean.TRUE.equals(s.getActivo()));
        if (invalido) {
            throw new DomainException("Subrubro inválido");
        }

        userInterestRepository.deleteByUserId(user.getId());
        // Hibernate ordena INSERT antes que DELETE en el mismo flush. Sin este
        // flush, volver a guardar un subrubro que el usuario ya tenía choca
        // contra uq_user_interest.
        userInterestRepository.flush();

        for (Subrubro subrubro : elegidos) {
            userInterestRepository.save(UserInterest.builder()
                    .user(user)
                    .subrubro(subrubro)
                    .level(nivelDe(niveles, subrubro.getId()))
                    .build());
        }

        marcarOnboarded(user);
    }

    @Transactional
    public void marcarOnboarded(User user) {
        if (user == null || user.getId() == null || user.isInterestsOnboarded()) {
            return;
        }
        user.setInterestsOnboarded(true);
        userRepository.save(user);
    }

    private static InterestLevel nivelDe(Map<Long, InterestLevel> niveles, Long subrubroId) {
        if (niveles == null) {
            return InterestLevel.CURIOSO;
        }
        return niveles.getOrDefault(subrubroId, InterestLevel.CURIOSO);
    }

    private static int ordenCategoria(String categoria) {
        try {
            return ExperienceCategory.valueOf(categoria).ordinal();
        } catch (IllegalArgumentException | NullPointerException e) {
            return Integer.MAX_VALUE;
        }
    }
}
