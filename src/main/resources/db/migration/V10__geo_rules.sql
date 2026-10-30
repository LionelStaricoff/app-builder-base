CREATE TABLE IF NOT EXISTS country_rules (
    id BIGSERIAL PRIMARY KEY,
    country_code VARCHAR(16) NOT NULL UNIQUE,
    country_name VARCHAR(128),
    currency_code VARCHAR(8) NOT NULL,
    min_fare NUMERIC(19,2) NOT NULL,
    requires_personal_identity BOOLEAN NOT NULL,
    requires_vehicle BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS city_rule_overrides (
    id BIGSERIAL PRIMARY KEY,
    country_rule_id BIGINT NOT NULL REFERENCES country_rules(id),
    city_name VARCHAR(128) NOT NULL,
    currency_code VARCHAR(8),
    min_fare NUMERIC(19,2),
    requires_personal_identity BOOLEAN,
    requires_vehicle BOOLEAN,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (country_rule_id, city_name)
);