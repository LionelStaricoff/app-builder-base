CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    ride_id BIGINT,
    type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    debt_delta NUMERIC(19,2) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(256),
    created_at TIMESTAMP NOT NULL
);