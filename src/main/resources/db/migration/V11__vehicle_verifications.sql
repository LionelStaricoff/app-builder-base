CREATE TABLE IF NOT EXISTS vehicle_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    provider_reference VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);