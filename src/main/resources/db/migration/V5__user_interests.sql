-- Intereses del usuario (B1).
-- El flag distingue "eligió cero intereses" de "nunca vio el onboarding":
-- sin él, quien saltea volvería a ser interceptado para siempre.
ALTER TABLE users ADD COLUMN IF NOT EXISTS interests_onboarded BOOLEAN NOT NULL DEFAULT FALSE;

-- Los usuarios que ya existen no pasan por el onboarding: no los molestamos.
-- En una base nueva la tabla está vacía y esto es un no-op.
UPDATE users SET interests_onboarded = TRUE;

-- Entidad intermedia CON atributo (level): no es un @ManyToMany.
CREATE TABLE IF NOT EXISTS user_interests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subrubro_id BIGINT NOT NULL REFERENCES subrubros(id),
    level VARCHAR(20) NOT NULL DEFAULT 'CURIOSO',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_interest UNIQUE (user_id, subrubro_id)
);

CREATE INDEX IF NOT EXISTS idx_user_interests_user ON user_interests(user_id);
