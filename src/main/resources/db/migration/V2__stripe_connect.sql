-- Stripe Connect: cuentas conectadas de creadores y registro de desembolsos.
--
-- Idempotente de punta a punta ("IF NOT EXISTS" en todo): esta migración
-- puede correr sobre un esquema que YA tiene estas tablas y esta columna,
-- porque las creó Hibernate con ddl-auto=update antes de que existiera
-- Flyway. En una base nueva las crea de cero.

CREATE TABLE IF NOT EXISTS connect_accounts (
    id BIGSERIAL PRIMARY KEY,
    country VARCHAR(2) NOT NULL,
    created_at TIMESTAMP,
    last_synced_at TIMESTAMP,
    status VARCHAR(255) NOT NULL,
    stripe_account_id VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS connect_payouts (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(10,2) NOT NULL,
    commission NUMERIC(10,2) NOT NULL,
    completed_at TIMESTAMP,
    currency VARCHAR(3) NOT NULL,
    failure_reason VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    requested_at TIMESTAMP,
    status VARCHAR(255) NOT NULL,
    stripe_transfer_id VARCHAR(255),
    user_id BIGINT NOT NULL REFERENCES users(id),
    payment_id BIGINT NOT NULL UNIQUE REFERENCES payments(id)
);

-- Índice para buscar pagos pendientes por creador
CREATE INDEX IF NOT EXISTS idx_connect_payouts_user_status ON connect_payouts(user_id, status);

-- Campo payout_status en payments (Stripe Connect).
-- VARCHAR(255) y no VARCHAR(20): es un @Enumerated(STRING) sin length, así que
-- Hibernate espera 255 (igual que status).
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payout_status VARCHAR(255);

-- Índice para buscar pagos con payout pendiente.
-- NOTA: era un índice PARCIAL (WHERE payout_status = 'PENDING'), pero H2 no
-- soporta índices filtrados y las migraciones también corren contra el H2 de
-- los tests. Se degrada a un índice normal: la tabla de pagos es chica y el
-- planificador lo usa igual para ese WHERE.
CREATE INDEX IF NOT EXISTS idx_payments_payout_status ON payments(payout_status);
