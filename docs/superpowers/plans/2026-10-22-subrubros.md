# Plan — Subrubros base (tabla + seeder idempotente + subrubro en Experience)

**Fecha:** 2026-10-22 · **Estado:** ✅ implementado · **Base:** `docs/superpowers/specs/2026-10-22-subrubros-base.md` y `docs/superpowers/specs/2026-10-07-shared-web-core-design.md` (§4 "se puede ampliar", §10 seed idempotente, §12 paso 2 entidades).

> **Qué entrega**: tabla `subrubros` sembrada, columna `subrubro_id` nullable + FK en `experiences`, repositorio, asociación navegable `Experience.subrubro`, y tests de idempotencia y persistencia. **Sin lógica de feed/filtro/UI** (out of scope, §6 del spec).

---

## 0. Decisiones finales (implementadas)

- **D1 — Tabla `subrubros`**: `id BIGSERIAL PK`, `nombre VARCHAR(80) NOT NULL`, `categoria VARCHAR(80) NOT NULL` (string, no FK a la enum), `descripcion TEXT`, `activo BOOLEAN DEFAULT TRUE`; índice único `(categoria, nombre)`.
- **D2 — Seeder**: `SubrubroSeeder implements CommandLineRunner`, idempotente **por fila** (`existsByNombreAndCategoria`) — NO por `count()`. Corre en todos los entornos porque los subrubros son datos de referencia; si se quiere kill-switch, se agrega `@ConditionalOnProperty` después.
- **D3 — `Experience.subrubro`**: `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "subrubro_id")`, **nullable**. La FK la crea la migración; Hibernate solo valida (`ddl-auto=validate`).
- **D4 — Naming**: `Subrubro` / `subrubro` / `subrubros` / `subrubro_id` (confirmado por el usuario).
- **D5 — Semilla**: 21 filas. Las categorías deben coincidir **EXACTO** con `ExperienceCategory` (`GASTRONOMIA` sin acento, `AVENTURA`, `CULTURAL`, `DEPORTIVA`, `SOCIAL`). `categoria` es string libre: ninguna FK lo valida, así que un acento de más rompe el filtro del feed en silencio.
- **D6 — Fuera de alcance**: feed, filtro, búsqueda, UI del creador, intereses de usuario, IA.

---

## 1. Archivos

Producción:

| Archivo | Rol |
|---|---|
| `src/main/resources/db/migration/V4__subrubros.sql` | Tabla + índice único + columna `subrubro_id` + FK `fk_experiences_subrubro` |
| `src/main/java/.../experience/entity/Subrubro.java` | Entidad |
| `src/main/java/.../experience/repository/SubrubroRepository.java` | `findByCategoria`, `existsByNombreAndCategoria` |
| `src/main/java/.../experience/SubrubroSeeder.java` | `CommandLineRunner` idempotente por fila |
| `src/main/java/.../experience/entity/Experience.java` | Asociación `subrubro` (LAZY, nullable) |

Tests:

| Archivo | Cubre |
|---|---|
| `src/test/java/.../experience/SubrubroRepositoryTests.java` | `existsByNombreAndCategoria` + unicidad `(categoria, nombre)` |
| `src/test/java/.../experience/SubrubroSeederTests.java` | Semilla completa, no duplica, **agrega filas faltantes** |
| `src/test/java/.../experience/ExperienceSubrubroTests.java` | Persistencia con y sin subrubro (LAZY resuelto post-`em.clear()`) |

---

## 2. Migración (V4)

```sql
CREATE TABLE IF NOT EXISTS subrubros (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    categoria VARCHAR(80) NOT NULL,
    descripcion TEXT,
    activo BOOLEAN DEFAULT TRUE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subrubros_categoria_nombre
    ON subrubros(categoria, nombre);

ALTER TABLE experiences ADD COLUMN IF NOT EXISTS subrubro_id BIGINT;

ALTER TABLE experiences
    ADD CONSTRAINT fk_experiences_subrubro
    FOREIGN KEY (subrubro_id) REFERENCES subrubros(id);
```

**Ojo:** `ADD CONSTRAINT` no soporta `IF NOT EXISTS` en Postgres. Flyway garantiza una sola ejecución por versión; si la V4 ya se aplicó en algún entorno, **no se edita**: se crea `V5__...`.

---

## 3. Entity `Experience` (estado final)

```java
/** Nullable: una experiencia puede no tener subrubro. */
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "subrubro_id")
private Subrubro subrubro;
```

`@Getter/@Setter` de Lombok generan `getSubrubro()`/`setSubrubro()`. No se usan getters manuales ni `@Builder.Default` en este campo.

---

## 4. Seeder (idempotencia real)

```java
@Override
public void run(String... args) {
    // Idempotente POR FILA, no por count(): si la tabla ya tiene filas y
    // mañana se agrega un subrubro a SEMILLA, un early-return por count()
    // lo dejaría sin insertar para siempre.
    for (String[] row : SEMILLA) {
        if (repository.existsByNombreAndCategoria(row[1], row[0])) continue;
        repository.save(Subrubro.builder()
            .nombre(row[1])
            .categoria(row[0])
            .descripcion(row.length > 2 ? row[2] : null)
            .activo(true)
            .build());
    }
}
```

---

## 5. Tests y reglas de la H2 compartida

La suite usa **una sola H2 en memoria** (`jdbc:h2:mem:sharedtest`) compartida entre clases, y el `CommandLineRunner` siembra 21 subrubros al arrancar el contexto. Consecuencias obligatorias:

1. **`@Transactional` en las 3 clases de test**: cada test hace rollback y no contamina al resto.
2. **Nombres fuera de la semilla** en los tests que insertan (`Kayak`, `Buceo`, `Kayak de mar`): si usás `Senderismo`/`AVENTURA` chocás con la fila sembrada y el test falla por unicidad.
3. **`repo.saveAndFlush(...)`** para testear constraints: el proxy del repositorio traduce a `DataIntegrityViolationException`. Un `em.flush()` crudo lanza `PersistenceException` y el `assertThrows` falla por tipo.
4. **`em.clear()`** antes de releer cuando se prueba la asociación LAZY: sin eso leés del persistence context y no probás nada.

---

## 6. Verificación

```powershell
.\mvnw.cmd test
```

**Esperado: BUILD SUCCESS.** Baseline: 318 tests en la corrida anterior; se eliminaron 14 tests duplicados de subrubros (7 clases basura en rutas inválidas) y quedaron 7 definitivos → **311 tests**.

---

## 7. Gotchas conocidos

- **LAZY + fuera de transacción** → `LazyInitializationException`. Los templates funcionan porque `spring.jpa.open-in-view` queda en su default (`true`); mapear a DTO en un service fuera de transacción requiere `JOIN FETCH`/`@EntityGraph`.
- **`ddl-auto=validate` no valida FKs ni nullability**: si la migración no crea la constraint, Hibernate no te avisa. Por eso la FK va explícita en la V4.
- **La semilla no valida contra el enum**: un error de tipeo en `categoria` compila, pasa los tests viejos y rompe el filtro. El test `siembraLaSemillaCompleta` asserta `GASTRONOMIA` para cubrirlo.

---

## 8. Commits

```
chore(gitignore): ignorar el export del prototipo en features/
feat(Subrubro): agregar tabla base, seeder idempotente y asociación en Experience
docs(plan): alinear el plan de subrubros con la implementación
```

---

## 9. Siguientes pasos (fuera de este plan)

- **Campos de contenido** (`includes` / `bring`) → `docs/superpowers/specs/2026-10-22-experience-content-fields.md`.
- **UI del creador**: dropdown de subrubro en el form (alimentado por `findByCategoria`).
- **Facet/filtro por subrubro** en feed y búsqueda.
- **Intereses del usuario**: otro spec/plan base (`user_interests`), sin tocar los subrubros de la experiencia.
