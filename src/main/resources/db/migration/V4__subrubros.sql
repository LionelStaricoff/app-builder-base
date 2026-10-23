-- Subrubros: tabla de referencia de subtemas bajo una categoría (spec §3.1).
-- Idempotente: IF NOT EXISTS para tabla/índice; semilla en seeder Java, no aquí.

CREATE TABLE IF NOT EXISTS subrubros (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    categoria VARCHAR(80) NOT NULL,
    descripcion TEXT,
    activo BOOLEAN DEFAULT TRUE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subrubros_categoria_nombre
    ON subrubros(categoria, nombre);

-- FK nullable en experiences: la entidad Experience.subrubro (@ManyToOne LAZY)
-- la exige y con ddl-auto=validate Hibernate falla al arrancar si no existe.
-- La constraint la crea esta migración: Hibernate solo valida, no altera.
ALTER TABLE experiences ADD COLUMN IF NOT EXISTS subrubro_id BIGINT;

ALTER TABLE experiences
    ADD CONSTRAINT fk_experiences_subrubro
    FOREIGN KEY (subrubro_id) REFERENCES subrubros(id);