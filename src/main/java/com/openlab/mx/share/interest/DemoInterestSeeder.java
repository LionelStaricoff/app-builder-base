package com.openlab.mx.share.interest;

import com.openlab.mx.share.experience.entity.Subrubro;
import com.openlab.mx.share.experience.repository.SubrubroRepository;
import com.openlab.mx.share.interest.entity.InterestLevel;
import com.openlab.mx.share.interest.entity.UserInterest;
import com.openlab.mx.share.interest.repository.UserInterestRepository;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Intereses de muestra para {@code demo_user}, para que el perfil y los ajustes
 * no arranquen vacíos en la demo. Necesita que DataSeeder (orden 0) ya haya
 * creado el usuario y que SubrubroSeeder (orden 1) ya haya cargado el catálogo.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoInterestSeeder implements CommandLineRunner {

    /** nombre del subrubro -> nivel. Cubre los cinco rubros. */
    private static final Map<String, InterestLevel> SEMILLA = new LinkedHashMap<>();

    static {
        SEMILLA.put("Senderismo", InterestLevel.CURIOSO);
        SEMILLA.put("Cata de cafés", InterestLevel.AFICIONADO);
        SEMILLA.put("Arte callejero", InterestLevel.CURIOSO);
        SEMILLA.put("Yoga al aire libre", InterestLevel.EXPERTO);
        SEMILLA.put("Juegos de mesa", InterestLevel.AFICIONADO);
    }

    private final UserRepository userRepository;
    private final SubrubroRepository subrubroRepository;
    private final UserInterestRepository userInterestRepository;

    @Override
    public void run(String... args) {
        User demo = userRepository.findByUsername("demo_user").orElse(null);
        if (demo == null || userInterestRepository.countByUserId(demo.getId()) > 0) {
            return;
        }

        Map<String, Subrubro> porNombre = new LinkedHashMap<>();
        subrubroRepository.findAll().forEach(s -> porNombre.putIfAbsent(s.getNombre(), s));

        SEMILLA.forEach((nombre, nivel) -> {
            Subrubro subrubro = porNombre.get(nombre);
            if (subrubro == null) {
                return;
            }
            userInterestRepository.save(UserInterest.builder()
                    .user(demo)
                    .subrubro(subrubro)
                    .level(nivel)
                    .build());
        });

        if (!demo.isInterestsOnboarded()) {
            demo.setInterestsOnboarded(true);
            userRepository.save(demo);
        }
    }
}
