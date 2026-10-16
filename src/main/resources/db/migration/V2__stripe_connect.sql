-- Stripe Connect: cuentas conectadas de creadores
CREATE TABLE connect_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    stripe_account_id VARCHAR(255) NOT NULL UNIQUE,
    country VARCHAR(2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    created_at TIMESTAMP,
    last_synced_at TIMESTAMP
);

-- Stripe Connect: registro de desembolsos a creadores
CREATE TABLE connect_payouts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    payment_id BIGINT NOT NULL UNIQUE REFERENCES payments(id),
    amount NUMERIC(10,2) NOT NULL,
    commission NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    stripe_transfer_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500),
    requested_at TIMESTAMP,
    completed_at TIMESTAMP,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE
);

-- Índice para buscar pagos pendientes por creador
CREATE INDEX idx_connect_payouts_user_status ON connect_payouts(user_id, status);

-- Campo payout_status en payments (Stripe Connect)
ALTER TABLE payments ADD COLUMN payout_status VARCHAR(20);

-- Índice para buscar pagos RELEASED con payout pendiente
CREATE INDEX idx_payments_payout_status ON payments(payout_status) WHERE payout_status = 'PENDING';
