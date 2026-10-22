package com.openlab.mx.share.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * La V3 de backfill (instant book) confirma reservas históricas que ya fueron
 * pagadas: PENDIENTE con pago HELD/RELEASED pasa a CONFIRMADA.
 *
 * <p>El test no depende de que Flyway haya corrido la V3 al arrancar (con datos
 * precargados no la cubriría): siembra dos filas nuevas y vuelve a ejecutar el
 * archivo SQL desde el classpath. Así el contenido del archivo es lo que se
 * prueba, y sirve igual sobre H2 (tests) que sobre Postgres (producción).
 */
@SpringBootTest
class InstantBookBackfillMigrationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void backfillConfirmaSoloReservasPagadas() throws IOException {
        // fila A: PENDIENTE + pago HELD  → debe confirmarse
        // fila B: PENDIENTE + pago PENDING → control: queda igual
        Long reservaA = insertReserva();
        Long reservaB = insertReserva();
        insertPayment(reservaA, "HELD");
        insertPayment(reservaB, "PENDING");

        try {
            jdbc.execute(leerMigracion());

            assertEquals("CONFIRMADA", statusDe(reservaA),
                    "reserva pagada (HELD) debe quedar CONFIRMADA");
            assertEquals("PENDIENTE", statusDe(reservaB),
                    "reserva sin pagar no se toca");
        } finally {
            jdbc.update("DELETE FROM payments WHERE reservation_id IN (?, ?)", reservaA, reservaB);
            jdbc.update("DELETE FROM reservations WHERE id IN (?, ?)", reservaA, reservaB);
        }
    }

    private String leerMigracion() throws IOException {
        try (InputStream in = new ClassPathResource("db/migration/V3__instant_book_backfill.sql")
                .getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String statusDe(Long id) {
        return jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, id);
    }

    /** Reserva PENDIENTE mínima: instance_id/user_id son anulables en el esquema. */
    private Long insertReserva() {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO reservations (attendees, created_at, status) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, 1);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, "PENDIENTE");
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private void insertPayment(Long reservaId, String status) {
        jdbc.update(
                "INSERT INTO payments (amount, commission, status, reservation_id) VALUES (?, ?, ?, ?)",
                new BigDecimal("100.00"), new BigDecimal("5.00"), status, reservaId);
    }
}
