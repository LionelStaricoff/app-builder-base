package com.openlab.mx.share.shared;

import com.openlab.mx.share.shared.config.H2DatasourceCondition;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga 2 usuarios demo (spec §10).
 *
 * <p><b>Solo contra H2.</b> El bean se crea únicamente si la base de datos es
 * H2 (ver {@link H2DatasourceCondition}): contra Postgres el seeder no existe
 * y no hay manera de que un despliegue siembre cuentas demo en la base real.
 * Los datos precargados son de pruebas, no de producción.
 *
 * <p>Además, el seeding se puede apagar por configuración con
 * {@code app.demo.enabled} (env {@code APP_DEMO_ENABLED}).
 */
@Component
@Conditional(H2DatasourceCondition.class)
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Order(0)
public class DataSeeder implements CommandLineRunner {

    /** Contraseña de los usuarios demo. Entorno: DEMO_PASSWORD (default en application.properties). */
    @Value("${app.demo.password:Clave1234!}")
    private String demoPassword;

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
        // Fail fast: una contraseña vacía (p. ej. DEMO_PASSWORD= en el .env)
        // dejaría usuarios demo con hash de "" → login sin clave. No seguimos.
        if (demoPassword == null || demoPassword.isBlank()) {
            throw new IllegalStateException(
                    "app.demo.password (env DEMO_PASSWORD) está vacío: se aborta el seeding de "
                            + "los usuarios demo para no crearlos con contraseña vacía.");
        }
        userRepository.save(User.builder()
                .username("demo_creator")
                .email("creator@shared.mx")
                .passwordHash(passwordEncoder.encode(demoPassword))
                .birthDate(java.time.LocalDate.of(1990, 3, 15))
                .avatarBase64(AVATAR_1PX)
                .city("CDMX")
                .lat(19.4326)
                .lng(-99.1332)
                .country("México")
                .currencyCode("MXN")
                .role(Role.CREATOR)
                .spPoints(0)
                .interestsOnboarded(true)
                .build());
        userRepository.save(User.builder()
                .username("demo_user")
                .email("user@shared.mx")
                .passwordHash(passwordEncoder.encode(demoPassword))
                .birthDate(java.time.LocalDate.of(1997, 7, 22))
                .avatarBase64(AVATAR_1PX)
                .city("CDMX")
                .lat(19.4326)
                .lng(-99.1332)
                .country("México")
                .currencyCode("MXN")
                .role(Role.USER)
                .spPoints(0)
                .interestsOnboarded(true)
                .build());
    }
}
