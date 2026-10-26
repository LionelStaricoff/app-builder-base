-- Preguntas públicas a la anfitriona (pieza "Preguntas" del detalle).
--
-- El detalle mostraba reseñas pero no dejaba preguntar nada antes de reservar:
-- la duda típica ("¿puedo ir aunque nunca haya tocado barro?") se resolvía por
-- chat privado DESPUÉS de pagar. Acá la pregunta y su respuesta quedan
-- públicas, que es lo que evita repetir la misma conversación con cada persona.
--
-- `answer` nullable = pregunta sin responder todavía; la UI la muestra así en
-- lugar de esconderla.
--
-- ddl-auto=validate: sin esta migración la app no arranca, porque Hibernate
-- solo comprueba el esquema, no lo crea.
CREATE TABLE IF NOT EXISTS experience_questions (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL REFERENCES experiences(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    body TEXT NOT NULL,
    answer TEXT,
    created_at TIMESTAMP NOT NULL,
    answered_at TIMESTAMP
);

-- El detalle pide siempre las preguntas de UNA experiencia, ordenadas por fecha.
CREATE INDEX IF NOT EXISTS idx_questions_experience ON experience_questions (experience_id);
