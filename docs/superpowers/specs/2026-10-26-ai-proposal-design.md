# Spec — Propuesta de experiencia con IA (pantalla 3 + borrador editable)

**Fecha:** 2026-10-26 · **Estado:** aprobada en chat por el usuario (4 decisiones de producto + diseño en 4 secciones) · **Base:** `docs/superpowers/specs/2026-10-22-experience-content-fields.md` (bloque de contenido `includes`/`bring`) y `docs/superpowers/plans/2026-10-22-user-interests.md` (onboarding de intereses: gates, whitelist `next`, `saltear`)

## 1. Objetivo

La app acompaña al usuario nuevo con una **propuesta de experiencia generada por IA a partir de sus intereses**, editable en pantalla y persistida como **borrador**, que después alimenta el form de alta existente. Es el hueco real del producto: hoy el usuario llega al feed sin saber qué publicar.

Reglas duras del diseño:

- La IA propone **solo el bloque de contenido** (`name`, `description`, `category`, `includes`, `bring`). Lo logístico (fecha, precio, cupos, punto de encuentro, fotos) se completa siempre en `/experiences/new`, con su validación de siempre: **cero duplicación del form**.
- La pantalla **nunca bloquea**: no traba el registro, "Saltear" siempre está disponible y un error de la IA se muestra con "Reintentar".
- Sin API key (local, tests, e2e) el flujo corre igual con un **stub determinista**.

## 2. Decisiones aprobadas por el usuario

1. **Stack: Spring AI 2.0.x** (línea compatible con Spring Boot 4.1.1 del proyecto), con **tres proveedores cableados** — Gemini (principal), OpenAI y Ollama — **cambiables por env var**, sin tocar código.
2. **No bloquea el registro**: la pantalla va después del onboarding de intereses y se puede saltear.
3. **Persistencia: borrador editable** en tabla nueva (varios borradores por usuario; la pantalla muestra el último). No es efímera, no vive en sesión.
4. **Degradación con stub determinista + flag/env**: sin key el flujo queda activo y testeable; con key real va el proveedor.
5. **Entrada:** intereses del onboarding como contexto + **texto libre opcional**; la propuesta se **edita en la misma pantalla** (solo contenido) y "Crear experiencia" pasa al form prellenado.
6. **Alcance:** pantalla 3 del onboarding (se ve una sola vez) **+** botón "Crear con IA" en `/experiences/new`.
7. **La ven todos**: la columna `proposal_onboarded` nace en `FALSE` **sin backfill** — los usuarios existentes también la atraviesan una vez (a diferencia de `interests_onboarded` en V5, que sí se backfilleó en TRUE).

## 3. Flujo

### Entradas

```
/register → login → /feed
   └─ gate 1 (existe): !interestsOnboarded → /onboarding/intereses → /feed
   └─ gate 2 (NUEVO):  !proposalOnboarded  → /onboarding/propuesta?next=feed
                          ├─ "Crear experiencia" → flag=true → /experiences/new?draft={id}
                          └─ "Saltear"           → flag=true → /feed

/experiences/new (quién pase el gate de KYC)
   └─ botón "Crear con IA" → /onboarding/propuesta?next=new
        └─ mismas acciones → flag=true → /experiences/new[?draft={id}]
```

### La pantalla (`templates/proposal/proposal.html`)

- Chips de los intereses del usuario + textarea **opcional** "Contá qué te gustaría ofrecer".
- **Generar propuesta**: bajo demanda (sin auto-generación al llegar: cada visita costaría tokens del proveedor por usuarios que luego saltean; con stub es instantáneo igual).
- Tras generar: los 5 campos de contenido **editables ahí** (nombre, descripción, categoría con select de labels — nunca el enum crudo —, qué incluye, qué llevar).
- Acciones: **Crear experiencia** (persiste → redirect al form), **Regenerar** (con el texto libre actual), **Saltear**.
- Error de la región generada (`aria-live="polite"`): "No pudimos generar la propuesta" + **Reintentar**; Saltear sigue vivo.
- Estética del sistema Ola 1: `.card`, `.section`, `btn-primary`/`btn-secondary`, tokens `--space-*`/`--color-*`; **sin `style=` inline ni emojis** (consistente con el pulido recién cerrado); labels asociados a cada input; targets ≥ 44px.

### Semántica de borradores (ambigua en el chat, acá queda fija)

| Acción | Efecto |
|---|---|
| `GET` sin `?draft=` | Muestra el **último** borrador propio si existe (editable, con su id como hidden); si no, solo el generador |
| `POST generar` | **Siempre INSERT** (fila nueva) → redirect `?draft={id}` (así "Regenerar" acumula historia sin pisar) |
| `POST crear` | **UPDATE** de la fila del hidden `draftId` (INSERT si viniera vacío) + `proposalOnboarded=true` → redirect al form |
| `GET ?draft={id}` | Precarga ese borrador **solo si es propio**; si no existe o es ajeno, se ignora (sin 404, sin filtrar existencia) |

## 4. Arquitectura

Paquete nuevo `com.openlab.mx.share.proposal` (controller, service, config, repository, entity, dto).

### 4.1 Puerto

```java
public interface ProposalGenerator {
    ExperienceProposal generate(ProposalRequest request);
}

public record ProposalRequest(List<InterestItem> intereses, String textoLibre) {}
public record InterestItem(String rubroLabel, String subrubroNombre, InterestLevel nivel) {}
public record ExperienceProposal(String name, String description,
        ExperienceCategory category, String includes, String bring) {}
```

El armado de `InterestItem` sale de `interestService.intereses(user)` / `niveles(user)` (ya existentes).

`ProposalGenerationException extends DomainException` (patrón de errores del proyecto).

### 4.2 Stub — `StubProposalGenerator`

Determinista, sin red: compone nombre/descripción de los rubros y niveles elegidos (y del texto libre si viene) con templates fijos en español, categoría = rubro dominante. Siempre produce una propuesta dentro de los límites del form (`includes`/`bring` ≤ 2000 chars, un ítem por línea).

### 4.3 IA — `AiProposalGenerator`

Spring AI `ChatClient`:

- Prompt de sistema en español: rol anfitrión de experiencias locales, pide **JSON con los 5 campos**, mismos límites del form, una entrada por línea en `includes`/`bring`, en español neutro.
- Salida estructurada con el converter JSON de Spring AI → `ExperienceProposal` + **validación de dominio post-parse** (categoría válida, textos no vacíos y dentro de límite).
- 1 reintento automático ante JSON inválido; si vuelve a fallar → `ProposalGenerationException`.
- Un `log.info` por invocación (proveedor, ok/error, ms). **Nunca** loguear la key ni el prompt completo.

### 4.4 Selección de beans y degradación — `ProposalConfig`

| Escenario | Bean activo | Comportamiento |
|---|---|---|
| `AI_CHAT_PROVIDER=none` (default) | `StubProposalGenerator` | flujo 100% activo sin key (es lo que corren los tests) |
| Proveedor con key presente | `AiProposalGenerator` | Gemini/OpenAI según provider; Ollama por su base-url (no necesita key) |
| Proveedor con key **ausente** | `StubProposalGenerator` + `log.warn` | degradación pedida: nunca se rompe el arranque |
| `ai.proposal.provider=stub` | `StubProposalGenerator` | fuerza stub (CI con env global de proveedor) |
| Error en runtime (red, rate limit, JSON irrecuperable) | — | `ProposalGenerationException` → pantalla con Reintentar/Saltear; **no** se guarda borrador a medias |

### 4.5 Configuración

`pom.xml`: importar `org.springframework.ai:spring-ai-bom` en `dependencyManagement` **fijando la última 2.0.GA disponible** (2.0 es la línea confirmada compatible; la versión exacta se toma de Maven Central al implementar, no se inventa acá) + starters `spring-ai-starter-model-google-genai`, `spring-ai-starter-model-openai`, `spring-ai-starter-model-ollama`. Con varios starters en classpath la property de selección es **obligatoria**, por eso el default es `none`.

`application.properties` (patrón de env del proyecto, igual que `identity.verification.enabled`):

```properties
# Switch único de proveedor: none | google-genai | openai | ollama
spring.ai.model.chat=${AI_CHAT_PROVIDER:none}
ai.proposal.provider=${AI_PROPOSAL_PROVIDER:auto}          # auto | stub
spring.ai.google.genai.api-key=${GEMINI_API_KEY:}
spring.ai.openai.api-key=${OPENAI_API_KEY:}
spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
spring.ai.google.genai.chat.model=${GEMINI_MODEL:gemini-2.5-flash}
spring.ai.openai.chat.options.model=${OPENAI_MODEL:gpt-4o-mini}
spring.ai.ollama.chat.options.model=${OLLAMA_MODEL:llama3.2}
```

Los prefijos de **Google GenAI están verificados** en la doc oficial de Spring AI (§6); los de OpenAI/Ollama se **reconfirmarán contra la doc de 2.0 al implementar** (la línea 2.0 renombró starters y propiedades).

## 5. Datos

`db/migration/V9__ai_proposal.sql` (nuevo; portable Postgres + H2, patrón V1/V5/V6):

```sql
-- Propuesta con IA (spec 2026-10-26-ai-proposal-design.md).
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
```

- Entity `ExperienceDraft` con `@Enumerated(EnumType.STRING)` para `category` y mapeo análogo a `Experience.includes/bring` (obligatorio: `ddl-auto=validate`).
- `User.proposalOnboarded` (boolean, `= false`), con getter/setter del patrón de `interestsOnboarded`.
- Repository: `findByUserIdOrderByUpdatedAtDesc` (+ `findTopByUserIdOrderByUpdatedAtDesc` para "el último").

## 6. Endpoints

| Ruta | Método | Comportamiento |
|---|---|---|
| `/onboarding/propuesta` | GET | Pantalla: intereses, último borrador si existe, `next` |
| `/onboarding/propuesta` | GET `?draft={id}` | Igual, precargando ese borrador si es propio |
| `/onboarding/propuesta/generar` | POST | `ProposalGenerator.generate(...)` → INSERT borrador → PRG a `?draft={id}`; ante `ProposalGenerationException` → PRG con `error` (Reintentar) |
| `/onboarding/propuesta/crear` | POST | Valida form de contenido (mismas constraints del form real) → UPDATE/INSERT borrador → flag → `redirect:/experiences/new?draft={id}` |
| `/onboarding/propuesta/saltear` | POST | Flag → redirect al destino |
| `/experiences/new?draft={id}` | GET | **Modificación**: precarga `name/description/category/includes/bring` en `experienceForm` si el borrador es propio; si no, form vacío. El gate de KYC (`redirect:/verificacion`) sigue **antes**: si redirige, el prefill se pierde (aceptable en V1) |

- Whitelist de `next`: `feed` (default) | `new` — misma técnica que `OnboardingController.destino()`: nunca redirigimos a un valor crudo.
- `currentUser.require()` en todos los POST (patrón OnboardingController); CSRF automático por `th:action`.
- **Gate nuevo en `FeedController`**: segundo `if`, idéntico al de intereses, después de él.

## 7. Cambios por archivo

**Producción**

| Archivo | Cambio |
|---|---|
| `pom.xml` | BOM Spring AI 2.0.x + 3 starters |
| `application.properties` | properties de §4.5 |
| `db/migration/V9__ai_proposal.sql` | nuevo (§5) |
| `user/entity/User.java` | campo `proposalOnboarded` |
| `experience/controller/FeedController.java` | gate 2 |
| `experience/controller/ExperienceController.java` | `form()` acepta `?draft=` y precarga si es propio; link "Crear con IA" en el model o directo en `experience/form.html` |
| `proposal/ProposalController.java` | nuevo (§6) |
| `proposal/service/ProposalService.java` | orquestación: genera, valida, persiste, flag |
| `proposal/config/ProposalConfig.java` | beans stub/IA + degradación (§4.4) |
| `proposal/generator/{ProposalGenerator,StubProposalGenerator,AiProposalGenerator}.java` | nuevo (§4.1–4.3) |
| `proposal/entity/ExperienceDraft.java` + repository + dtos | nuevo (§5) |
| `shared/DataSeeder.java` | `.proposalOnboarded(true)` en `demo_user` y `demo_creator` (son fixtures: los tests de feed no deben atravesar la pantalla) |
| `templates/proposal/proposal.html` | nuevo (§3) |
| `templates/experience/form.html` | botón/link "Crear con IA" → `/onboarding/propuesta?next=new` |

**Datos**: `V9` (§5). Usuarios reales migrados → `FALSE` → ven la pantalla una vez.

## 8. Pruebas (TDD)

**Nuevas** (asignadas de antemano para que el plan no tenga que decidirlo):

- `StubProposalGeneratorTest`: determinismo (misma entrada → misma salida), siempre dentro de límites del form, sin red.
- `AiProposalGeneratorTest` (ChatClient mockeado): JSON válido → `ExperienceProposal`; JSON inválido → 1 reintento → `ProposalGenerationException`. Nunca red real.
- `ProposalConfigTest`: sin key → stub; `none` → stub; `provider=stub` → stub con key presente; provider+key → IA.
- `ProposalControllerTests` (MockMvc): GET renderiza intereses y último borrador; generar hace PRG y crea fila; crear valida (`@NotBlank`, ≤ 2000) y redirige a `/experiences/new?draft=` con flag en true; saltear → destino; `next=//evil` → cae a `/feed`; sin sesión → login. Prefill: `?draft=` propio precarga, ajeno/inexistente ignora.
- Gate: `GET /feed` con `proposalOnboarded=false` → redirect `/onboarding/propuesta`; con true → 200.
- `ExperienceDraftRepositoryTests`: la migración V9 aplica en H2 y el roundtrip del repository funciona.

**Ajustes inevitables por el gate** (los tests que hacen `GET /feed` autenticado con usuario sembrado pasan a necesitar `proposalOnboarded=true` en su builder/seeder — mismo churn que tuvo el gate de intereses):

| Test | Qué toca |
|---|---|
| `DataSeeder` | `.proposalOnboarded(true)` en los 2 usuarios demo (los cubre `E2eSmokeTests`, `OnboardingControllerTests:93`, `RoleRefreshFilterTest` si usan demo) |
| `FeedAndFavoritesControllerTests` | líneas con `.with(user(username))` (225, 245, 365): el builder del usuario del test |
| El resto de `GET /feed` con sesión | se confirman al correr la suite completa (`mvnw test`, hoy 378 en verde) |

**E2E (`e2e/tests/proposal.spec.ts`)**: registra usuario nuevo por API, completa el onboarding de intereses por API, y por UI: feed → pantalla 3 → Generar (stub) → editar contenido → Crear experiencia → **verifica `/experiences/new?draft=` prellenado**; caso Saltear (no reaparece); entry 2 desde `/experiences/new`. Los 6 e2e actuales quedan intactos (los demo users llevan el flag).

**Verificación final**: `cd e2e && npx playwright test` + `mvnw test` + `graft build`.

## 9. Riesgos y rollback

- **Mayor:** el gate 2 y su churn de tests (tabla §8). Mitigación: TDD del gate primero + suite completa.
- **Migración V9**: `ALTER TABLE ... DEFAULT FALSE` + `CREATE TABLE IF NOT EXISTS` — idempotente y sin UPDATE de datos; corre en H2 de tests. Rollback = `V10` que dropea la tabla/columna si hiciera falta (nunca reescribir V9 ya aplicada).
- **Spring AI 2.0 con Boot 4.1**: verificado en doc oficial (2.0.x soporta Boot 4.0/4.1); el riesgo residual son prefijos de propiedades de OpenAI/Ollama (§4.5) — se confirman al implementar, con los tests de `ProposalConfig` como red.
- **Arranque sin env**: si los starters rompieran el boot con `model.chat=none`, lo vería de inmediato la suite existente (378 tests) — es el primer check del plan.
- **Deshacer el feature**: revert de los commits; la columna V9 puede quedarse inerte (default false + gate en un controller borrado = sin efecto).

## 10. Fuera de alcance

- Listado de borradores / "mis borradores" (la tabla ya lo soporta; UI aparte).
- Rate-limit o cuota de llamadas por usuario (V1 sin límite; el stub y el switch `none` ya protegen el costo por defecto).
- Imágenes generadas por IA, o IA para lo logístico (fecha/precio/cupos).
- Editar el borrador desde el form `/experiences/new` (el form no tiene "guardar como borrador": editar contenido es en la pantalla 3).
- Mover la entrada "Crear con IA" a Mis experiencias.
- Historial/auditoría de prompts y respuestas (solo `log.info` operativo).

## 11. Constraints globales

- **Marca visual intacta**: la pantalla usa clases y variables existentes (`.card`, `.section`, `.btn-*`, `--space-*`, los 5 temas `data-style`). Sin `style=` inline, sin emojis, SVG inline con `currentColor` si hace falta un ícono.
- Flyway administra el esquema; `ddl-auto=validate`; SQL portable Postgres + H2 (sin `MODE=PostgreSQL` en el H2 de tests).
- Mensajes de commit en castellano, Conventional Commits con scope de clase.
- Todo se acumula en la rama `feature/v2` (commits por tarea terminada).

## 12. Referencias verificadas (2026-10-26)

- Spring AI 2.0.x soporta Spring Boot 4.0.x/4.1.x: `docs.spring.io/spring-ai/reference/getting-started.html`.
- Starter Gemini + prefijos: `docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html` (`spring-ai-starter-model-google-genai`, `spring.ai.model.chat=google-genai`, `spring.ai.google.genai.api-key`, modelo default `gemini-2.5-flash`).
- Selección de modelo multi-starter: `docs.spring.io/spring-ai/reference/upgrade-notes.html` (`spring.ai.model.chat=<provider|none>`; con varios starters la property es obligatoria).
