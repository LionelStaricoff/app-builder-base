# Spec — Subrubros: base pura (tabla + seeder idempotente + Campo)

**Fecha:** 2026-10-22 · **Estado:** aprobado en chat (3 pasos) · **Base:** `docs/superpowers/specs/2026-10-07-shared-web-core-design.md` (§4 schema "se puede ampliar", §12 paso 2 experiencias, §10 seed idempotente) y `docs/superpowers/specs/2026-10-22-instant-book-design.md` (§10 constraints globales; commit `f08e22c` ya mostró el patrón de campo nuevos en `Experience`).

> **Qué es un subrubro**: un subtema estrecho bajo una categoría. No es una categoría nueva, ni un interest del usuario, ni un facet — es un descriptor de experiencia existente que permite estilo Aventura→Senderismo / Ciclismo / Escalada sin forkear la enum de 5 categorías `(ajustables)`.

> **Este spec cubre SOLO la base pura**: qué almacena y cómo se siembra. La lógica de búsqueda/filtrado/feed se decide en plan/implementación aparte; hoy ningún feature la usa, así que la base debe poder crearse sin que nada se rompa.

> **Estado de implementación (2026-10-22):** ver `docs/superpowers/plans/2026-10-22-subrubros.md`. Ajustes finales respecto de este spec: el seeder es `CommandLineRunner` idempotente **por fila** (no `@PostConstruct` por `count()`); el campo quedó como `Experience.subrubro` con `@ManyToOne(LAZY)` nullable y FK real; y la tabla/columna las crea `V4__subrubros.sql` porque `ddl-auto=validate` exige migración — la **semilla** sigue en Java, sin ids hardcodeados.

## 1. Objetivo

Dar al creador una primera forma de expresar subtema de su experiencia y a Shared una tabla de subrubros sembrados que en el futuro alimente descubrimiento/facet/IA sin reescribir la enum de 5 categorías.

## 2. Decisiones aprobadas por el usuario

1. **Base pura**: tabla `subrubros` + seeder `@PostConstruct` idempotente + campo `subrubroId` (FK nullable) en `Experience` (o `subrubro` nombre del campo, a definir). Sin lógica de feed/filtrado todavía.
2. **Semilla en app, no en SQL migración volátil**: el seeder `@PostConstruct` idempotente (igual que `DataSeeder` de usuarios demo). Sin `Vn__subrubros.sql` que presuma ids.
3. **El campo es nullable**: si el creador no elige subrubro, la experiencia es válida sin él. Sin `NOT NULL` que rompa experiencias existentes.

## 3. Decisiones técnicas (a codificar, a confirmar con arquitectura)

### D1 — Tabla `subrubros`

| Columna | Tipo | NotNull | Observación |
|---|---|---|---|
| `id` | BIGSERIAL PK | sí | |
| `nombre` | VARCHAR(80) | sí | Nombre del subrubro, único por categoría |
| `categoria` | VARCHAR(80) | sí | Categoría padre (string opcional, o FK a enum — decidir en plan; hoy string para flexibilidad; si se hace FK a enum, la tabla se siembra con nombres de enum válidos) |
| `descripcion` | TEXT | no | Opcional — puede usarse para defensa de búsqueda o muestra |
| `activo` | BOOLEAN | no | default true; permite desactivar sin borrar |

**Índice único**: `(categoria, nombre)` — un subrubro no se repite bajo la misma categoría.

**Sin búsqueda LIKE global todavía**: el seeder siembra con nombres explícitos. La búsqueda/facet viene en plan aparte (outside this spec).

### D2 — `@PostConstruct` en un componente dedicado (no `DataSeeder` existente)

Patrón `DataSeeder` (§10 core): bean condicional, idempotente. Para subrubros, montar un componente separado (`SubrubroSeeder` o `SubrubroInitializer`) con `@PostConstruct` que:

- consulta `count` de `subrubros`;
- si `count == 0`, inserta la semilla;
- repite la consulta (por si otro hilo/instancia ya sembró);
- si ya existe (count > 0), no hace nada (idempotente).

La semilla inicial propuesta (ajustable): dependency de categorías enum existentes — e.g.:

| categoría | subrubros semilla |
|---|---|
| `AVENTURA` | Senderismo, Ciclismo de montaña, Escalada, Rafting, Parapente |
| `GASTRONOMÍA` | Degustación de vinos, Cata de cafés, Cocina local, Barbacoa/tradición |
| `CULTURAL` | Museo/arqueología, Arte callejero, Tradiciones/folclor, Ferias/mercados |
| `DEPORTIVA` | Fútbol callejero, Balonmano/salón, Yoga al aire libre, Tenis/pádel |
| `SOCIAL` | Meetups/lenguas, Juegos de mesa, Intercambio cultural, fiesta vecinal |

Números exactos y nombres ajustables en la implementación. El seeder no es contrato contractual de nombres — es semilla demo/seed; lo que importa es que el mecanismo sea idempotente y extensible.

Si en el futuro la semilla se quiere versionar (v1, v2), se decide aparte.

### D3 — Campo `Experience.subrubroId` (o `subrubro`)

- FK nullable a `subrubros.id`.
- No `NOT NULL`, no `unique`, no required en el form.
- Sin lógica de edición de subrubro por el creador todavía — se decide en plan/implementación si el creador elige subrubro en el form o no. Hoy el form puede ignorar el campo; la base pura lo crea sin tocar el form.

### D4 — Sin lógica de feed/filtrado/IA todavía

Este spec no toca `FeedService`, `ExperienceFeedDTO`, ni filtros, ni búsqueda. La existencia de la tabla y el campo no afectan el feed; si en el futuro se filtra por subrubro, ese cambio entra en otra spec/plan.

### D5 — Enum categoría no cambia

`ExperienceCategory` (5 valores, ajustables) pasa a ser una capa de "categoría padre" sin perder nada. La tabla de subrubros siembra nombres de subrubros por categoría, pero no fuerza FK a la enum; puede ser string libre o FK dependiendo de decisión arquitectónica en plan.

## 4. Schema

`"se puede ampliar"` (core spec §4).

Tabla nueva: `subrubros` (D1).

Campo nuevo en `Experience`: `subrubro_id` BIGINT nullable FK → `subrubros.id` (D3).

No hay migración en este spec todavía — la creación de tabla/ campo puede ser Hibernate `ddl-auto=update` en dev (ya existe el patrón con `V2__stripe_connect.sql` siendo el precedente de "tener tabla real en Flyway" vs Hibernate-only). Queda decisión de plan: Flyway explícito o Hibernate update.

## 5. Seeds / semilla (solo mecanismo, nombres ajustables)

- El seeder se ejecuta al arrancar.
- Inserta sólo si la tabla está vacía (count == 0).
- La semilla inicial se elige en la implementación; la lista de arriba es propuesta de ejemplo, no contractual.

Nada de semilla de experiencias con subrubro asignado — eso rompería "base pura sin lógica". El seeder de usuarios demo y de subrubros son independientes.

## 6. Out of scope

- Filtro/feed/facet por subrubro
- Búsqueda LIKE global de subrubro
- Asignación de subrubro al creador en el form (sin UI todavía)
- Intereses/preferencias del usuario (esa es otra base: tabla `intereses_usuario` o `user_interests`, aparte; no toca los subrubros de la experiencia)
- IA / propuestas con Spring AI (otro epístola)
- Categorías nuevas en la enum (solo ajustable, no endpoint de gestión de categorías en este spec)
- Borrado/actualización de subrubros por admin (sin UI; fuera)

## 7. Constraints / marca intacta (§10 instant book design)

- Nada de CSS ni layout cambia — base pura.
- Sin `ddl` defaults problemáticos para H2/Postgres (boolean, bigserial, varchar).

## 8. Riesgos y rollback

- **Mayor**: confundir "subrubro del creador" con "preferencia del usuario". Mantener separados — este spec es solo la tabla de referencia de experiencia.
- **Mayor**: FK no nullable que rompa historial de experiencias existentes — ste spec exige nullable (D3).
- **Rollback**: si la tabla se crea con Hibernate, basta con quitar el campo de la entidad y dejar que Hibernate recalibre (o migración inversa si es Flyway). Sin datos históricos perdidos por ser nullable y sin semilla de experiencias.

## 9. Pruebas (TDD, asignadas de antemano)

En `SubrubroSeederTests` o `DataSeederTests` (bajo `src/test/...shared/` o `...experience/`):

- Al arrancar con tabla vacía, el seeder inserta la semilla (count == 0 → semilla esperada tras `@PostConstruct`)
- Al rearrancar con semilla ya presente, no duplica (idempotente: count > 0 → sin cambios)
- Si se borra la tabla y se rearranca, re-semilla (caso de "perdón")

En `ExperienceEntityTests` / repository:

- Persistir experiencia con `subrubroId` → FK válido, `subrubro` trae nombre/categoría
- Persistir experiencia sin `subrubroId` → válido, null seguro

En `SubrubroRepository` (si se crea):

- findByCategoria o similar (solo existencia de repo; lógica de uso aparte)

La carta de tests concreta se resuelve en el plan de implementación, no aquí.

## 10. Seguimiento / extensión

Esta base es la mesa de entrada para los planes que la usan:

- **Facet/filtro por subrubro** → plan aparte (feed + búsqueda)
- **UI del creador para elegir subrubro** → plan aparte (form + dropdown sembrado)
- **Intereses del usuario** → otro spec/plan base (tabla user_interests, semilla de intereses, vinculación a subrubros cuando se decida)
- **IA propuestas con Spring AI** → otro epístola standalone
