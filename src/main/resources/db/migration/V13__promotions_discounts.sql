CREATE TABLE IF NOT EXISTS promotions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    commission_rate NUMERIC(5,4) NOT NULL,
    target_passenger_count INTEGER NOT NULL,
    passenger_counting_rule VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS passenger_discounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL,
    value_amount NUMERIC(19,2) NOT NULL,
    passenger_id BIGINT,
    starts_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    max_usage INTEGER,
    usage_count INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);