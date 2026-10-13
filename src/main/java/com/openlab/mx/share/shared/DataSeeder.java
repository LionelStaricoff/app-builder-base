package com.openlab.mx.share.shared;

import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga 2 usuarios demo solo si la base esta vacia (spec §10).
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    static final String DEMO_PASSWORD = "Clave1234!";
    private static final String AVATAR_1PX =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Idempotente: recrea los demos si faltan (una BD compartida puede
        // perderlos; sin ellos la suite de tests y el login demo se rompen).
        if (userRepository.existsByUsername("demo_creator")
                && userRepository.existsByUsername("demo_user")) {
            return;
        }
        userRepository.save(User.builder()
                .username("demo_creator")
                .email("creator@shared.mx")
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .birthDate(java.time.LocalDate.of(1990, 3, 15))
                .avatarBase64(AVATAR_1PX)
                .city("CDMX")
                .lat(19.4326)
                .lng(-99.1332)
                .country("México")
                .currencyCode("MXN")
                .role(Role.CREATOR)
                .spPoints(0)
                .build());
        userRepository.save(User.builder()
                .username("demo_user")
                .email("user@shared.mx")
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .birthDate(java.time.LocalDate.of(1997, 7, 22))
                .avatarBase64(AVATAR_1PX)
                .city("CDMX")
                .lat(19.4326)
                .lng(-99.1332)
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .build());
    }
}
