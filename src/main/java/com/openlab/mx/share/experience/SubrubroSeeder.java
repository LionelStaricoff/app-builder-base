package com.openlab.mx.share.experience;

import com.openlab.mx.share.experience.entity.Subrubro;
import com.openlab.mx.share.experience.repository.SubrubroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(1)
public class SubrubroSeeder implements CommandLineRunner {

    private static final String[][] SEMILLA = {
        {"AVENTURA", "Senderismo"},
        {"AVENTURA", "Ciclismo de montaña"},
        {"AVENTURA", "Escalada"},
        {"AVENTURA", "Rafting"},
        {"AVENTURA", "Parapente"},
        {"GASTRONOMIA", "Degustación de vinos"},
        {"GASTRONOMIA", "Cata de cafés"},
        {"GASTRONOMIA", "Cocina local"},
        {"GASTRONOMIA", "Barbacoa y tradición"},
        {"CULTURAL", "Museo/arqueología"},
        {"CULTURAL", "Arte callejero"},
        {"CULTURAL", "Tradiciones/folclor"},
        {"CULTURAL", "Ferias/mercados"},
        {"DEPORTIVA", "Fútbol callejero"},
        {"DEPORTIVA", "Balonmano/salón"},
        {"DEPORTIVA", "Yoga al aire libre"},
        {"DEPORTIVA", "Tenis/pádel"},
        {"SOCIAL", "Meetups/lenguas"},
        {"SOCIAL", "Juegos de mesa"},
        {"SOCIAL", "Intercambio cultural"},
        {"SOCIAL", "Fiesta vecinal"},
    };

    private final SubrubroRepository repository;

    @Override
    public void run(String... args) {
        // Idempotente POR FILA, no por count(): si la tabla ya tiene filas y
        // mañana se agrega un subrubro a SEMILLA, un early-return por count()
        // lo dejaría sin insertar para siempre.
        for (String[] row : SEMILLA) {
            if (repository.existsByNombreAndCategoria(row[1], row[0])) continue;
            repository.save(Subrubro.builder()
                .nombre(row[1])
                .categoria(row[0])
                .descripcion(row.length > 2 ? row[2] : null)
                .activo(true)
                .build());
        }
    }
}