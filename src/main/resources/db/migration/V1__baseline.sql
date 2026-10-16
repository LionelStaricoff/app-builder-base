-- Baseline: todas las tablas existentes del proyecto antes de Flyway.
-- Usa CREATE TABLE IF NOT EXISTS para ser idempotente.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    avatar_base64 TEXT,
    city VARCHAR(255),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    location_country VARCHAR(255),
    country VARCHAR(255),
    currency_code VARCHAR(3) DEFAULT 'MXN',
    role VARCHAR(20) NOT NULL,
    sp_points INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS experiences (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    duration_minutes INT NOT NULL,
    min_capacity INT NOT NULL,
    max_capacity INT NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    recurrence_type VARCHAR(20) NOT NULL,
    meeting_lat DOUBLE PRECISION NOT NULL,
    meeting_lng DOUBLE PRECISION NOT NULL,
    currency_code VARCHAR(3) DEFAULT 'MXN',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    rating_average DOUBLE PRECISION DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS experience_instances (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL REFERENCES experiences(id),
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) DEFAULT 'SCHEDULED',
    archived BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS experience_stops (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL REFERENCES experiences(id),
    position INT NOT NULL,
    place_name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS experience_schedules (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL REFERENCES experiences(id),
    date DATE,
    day_of_week VARCHAR(10),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    range_start DATE,
    range_end DATE
);

CREATE TABLE IF NOT EXISTS experience_images (
    id BIGSERIAL PRIMARY KEY,
    experience_id BIGINT NOT NULL REFERENCES experiences(id),
    data TEXT NOT NULL,
    position INT NOT NULL
);

CREATE TABLE IF NOT EXISTS ratings (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES experience_instances(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    stars INT NOT NULL,
    CONSTRAINT uk_rating_instance_user UNIQUE (instance_id, user_id)
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES experience_instances(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    text TEXT NOT NULL,
    CONSTRAINT uk_comment_instance_user UNIQUE (instance_id, user_id)
);

CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES experience_instances(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    attendees INT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDIENTE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE REFERENCES reservations(id),
    amount NUMERIC(10,2) NOT NULL,
    commission NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'HELD',
    held_at TIMESTAMP,
    released_at TIMESTAMP,
    payout NUMERIC(10,2),
    refund NUMERIC(10,2),
    stripe_session_id VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS stripe_event (
    event_id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    experience_id BIGINT NOT NULL REFERENCES experiences(id),
    CONSTRAINT uk_favorites_user_experience UNIQUE (user_id, experience_id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES experience_instances(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    link VARCHAR(500),
    read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS didit_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    received_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS didit_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    didit_session_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    didit_status VARCHAR(255),
    verified_at TIMESTAMP,
    decided_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_viewed_experiences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    experience_id BIGINT NOT NULL,
    viewed_at TIMESTAMP NOT NULL
);
