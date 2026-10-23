-- Contenido estructurado de la experiencia (spec
-- docs/superpowers/specs/2026-10-22-experience-content-fields.md).
--
-- Dos bloques de texto libre, uno por linea. Nullable a proposito: las
-- experiencias existentes quedan en NULL y la card del feed no los renderiza
-- (silencio, no placeholders).
--
-- ddl-auto=validate: sin esta migracion la app no arranca, porque Hibernate
-- solo comprueba el esquema, no lo crea.
ALTER TABLE experiences ADD COLUMN IF NOT EXISTS includes TEXT;
ALTER TABLE experiences ADD COLUMN IF NOT EXISTS bring TEXT;
