-- Baseline (V1): el esquema de la base que ya existía antes de Flyway.
--
-- Generado desde el information_schema de la base real, para que una base
-- NUEVA quede idéntica a la existente (mismos tipos, longitudes y nullability).
-- Todo es "IF NOT EXISTS": se puede aplicar sobre un esquema que ya tenga las
-- tablas (idempotente).
--
-- OJO: el esquema lo administraba Hibernate con ddl-auto=update, que NUNCA
-- altera tipos ni saca columnas. Por eso las longitudes de acá son las que
-- dejó Hibernate (255 por defecto en los enums @Enumerated(STRING)), y no las
-- "bonitas" tipo VARCHAR(20).
--
-- El orden importa: las tablas se crean de padres a hijos, porque las FKs
-- referencian tablas que todavía no existen si el orden es alfabético.

-- --- Sin dependencias -----------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    avatar_base64 TEXT,
    birth_date DATE NOT NULL,
    city VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    sp_points INTEGER NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    country VARCHAR(255),
    currency_code VARCHAR(255),
    location_country VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS stripe_event (
    event_id VARCHAR(255) NOT NULL PRIMARY KEY,
    processed_at TIMESTAMP,
    type VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS didit_webhook_events (
    event_id VARCHAR(255) NOT NULL PRIMARY KEY,
    received_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_viewed_experiences (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    viewed_at TIMESTAMP NOT NULL
);

-- --- Dependen de users ----------------------------------------------
CREATE TABLE IF NOT EXISTS experiences (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    max_capacity INTEGER NOT NULL,
    meeting_lat DOUBLE PRECISION NOT NULL,
    meeting_lng DOUBLE PRECISION NOT NULL,
    min_capacity INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    rating_average DOUBLE PRECISION NOT NULL,
    recurrence_type VARCHAR(255) NOT NULL,
    status VARCHAR(255),
    creator_id BIGINT REFERENCES users(id),
    currency_code VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    link VARCHAR(255),
    message TEXT NOT NULL,
    read BOOLEAN NOT NULL,
    type VARCHAR(255) NOT NULL,
    user_id BIGINT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS didit_verifications (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP,
    decided_at TIMESTAMP,
    didit_session_id VARCHAR(255),
    didit_status VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP,
    user_id BIGINT NOT NULL UNIQUE,
    verified_at TIMESTAMP
);

-- --- Dependen de experiences ----------------------------------------
CREATE TABLE IF NOT EXISTS experience_instances (
    id BIGSERIAL PRIMARY KEY,
    archived BOOLEAN NOT NULL,
    date DATE NOT NULL,
    end_time TIME NOT NULL,
    start_time TIME NOT NULL,
    status VARCHAR(255),
    experience_id BIGINT REFERENCES experiences(id)
);

CREATE TABLE IF NOT EXISTS experience_stops (
    id BIGSERIAL PRIMARY KEY,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    place_name VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    experience_id BIGINT REFERENCES experiences(id)
);

CREATE TABLE IF NOT EXISTS experience_schedules (
    id BIGSERIAL PRIMARY KEY,
    date DATE,
    day_of_week SMALLINT,
    end_time TIME NOT NULL,
    range_end DATE,
    range_start DATE,
    start_time TIME NOT NULL,
    experience_id BIGINT REFERENCES experiences(id)
);

CREATE TABLE IF NOT EXISTS experience_images (
    id BIGSERIAL PRIMARY KEY,
    data TEXT NOT NULL,
    position INTEGER NOT NULL,
    experience_id BIGINT REFERENCES experiences(id)
);

CREATE TABLE IF NOT EXISTS favorites (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT REFERENCES experiences(id),
    user_id BIGINT REFERENCES users(id),
    CONSTRAINT uk_favorites_user_experience UNIQUE (user_id, experience_id)
);

-- --- Dependen de experience_instances ------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    attendees INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL,
    instance_id BIGINT REFERENCES experience_instances(id),
    user_id BIGINT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    text TEXT NOT NULL,
    instance_id BIGINT REFERENCES experience_instances(id),
    user_id BIGINT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS ratings (
    id BIGSERIAL PRIMARY KEY,
    stars INTEGER NOT NULL,
    instance_id BIGINT REFERENCES experience_instances(id),
    user_id BIGINT REFERENCES users(id),
    CONSTRAINT uk_rating_instance_user UNIQUE (instance_id, user_id)
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    instance_id BIGINT REFERENCES experience_instances(id),
    user_id BIGINT REFERENCES users(id),
    CONSTRAINT uk_comment_instance_user UNIQUE (instance_id, user_id)
);

-- --- Dependen de reservations ---------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(10,2) NOT NULL,
    commission NUMERIC(10,2) NOT NULL,
    held_at TIMESTAMP,
    payout NUMERIC(10,2),
    refund NUMERIC(10,2),
    released_at TIMESTAMP,
    status VARCHAR(255) NOT NULL,
    reservation_id BIGINT REFERENCES reservations(id) UNIQUE,
    stripe_payment_intent_id VARCHAR(255),
    stripe_session_id VARCHAR(255)
);
