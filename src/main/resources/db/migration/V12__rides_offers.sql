CREATE TABLE IF NOT EXISTS rides (
    id BIGSERIAL PRIMARY KEY,
    rider_id BIGINT NOT NULL,
    driver_id BIGINT,
    origin_lat DOUBLE PRECISION NOT NULL,
    origin_lng DOUBLE PRECISION NOT NULL,
    destination_lat DOUBLE PRECISION,
    destination_lng DOUBLE PRECISION,
    origin_text VARCHAR(255),
    destination_text VARCHAR(255),
    country_code VARCHAR(16) NOT NULL,
    city_name VARCHAR(128),
    currency_code VARCHAR(8) NOT NULL,
    proposed_fare NUMERIC(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ride_offers (
    id BIGSERIAL PRIMARY KEY,
    ride_id BIGINT NOT NULL REFERENCES rides(id),
    driver_id BIGINT NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rides_rider ON rides (rider_id);
CREATE INDEX IF NOT EXISTS idx_ride_offers_ride ON ride_offers (ride_id);