-- V3 (instant book): backfill de reservas históricas.
--
-- Al quitar la confirmación manual del creador, las reservas que YA fueron
-- pagadas antes del cambio quedaban eternamente PENDIENTE: nadie puede
-- confirmarlas a mano y el chat nunca se abría. Toda PENDIENTE con un pago
-- retenido (HELD) o ya liberado (RELEASED) pasa a CONFIRMADA.
--
-- Idempotente: una segunda corrida no encuentra PENDIENTE pagadas. Sin alias
-- en el UPDATE y sin funciones de motor: corre igual en Postgres e H2.

UPDATE reservations
   SET status = 'CONFIRMADA'
 WHERE status = 'PENDIENTE'
   AND EXISTS (SELECT 1 FROM payments
                WHERE payments.reservation_id = reservations.id
                  AND payments.status IN ('HELD', 'RELEASED'));
