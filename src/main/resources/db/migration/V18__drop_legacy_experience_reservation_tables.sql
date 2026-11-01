-- Limpieza final T8: drop de tablas legacy experience/reservation y columnas hu�rfanas.
ALTER TABLE payments DROP COLUMN IF EXISTS reservation_id;

DROP TABLE IF EXISTS user_viewed_experiences CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS ratings CASCADE;
DROP TABLE IF EXISTS comments CASCADE;
DROP TABLE IF EXISTS reservations CASCADE;
DROP TABLE IF EXISTS favorites CASCADE;
DROP TABLE IF EXISTS experience_images CASCADE;
DROP TABLE IF EXISTS experience_schedules CASCADE;
DROP TABLE IF EXISTS experience_stops CASCADE;
DROP TABLE IF EXISTS experience_instances CASCADE;
DROP TABLE IF EXISTS experiences CASCADE;
