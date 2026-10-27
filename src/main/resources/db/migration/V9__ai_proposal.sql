-- Propuesta con IA (spec docs/superpowers/specs/2026-10-26-ai-proposal-design.md).
-- A diferencia de interests_onboarded (V5): NO se backfillea. Los usuarios
-- existentes ven la pantalla una vez (decisión del usuario).
ALTER TABLE users ADD COLUMN IF NOT EXISTS proposal_onboarded BOOLEAN NOT NULL DEFAULT FALSE;

-- Borradores de propuesta: varios por usuario; la pantalla muestra el último.
CREATE TABLE IF NOT EXISTS experience_drafts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(255) NOT NULL,
    includes TEXT,
    bring TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_experience_drafts_user ON experience_drafts(user_id);
