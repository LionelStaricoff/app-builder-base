# Intereses de usuario (B1) - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un usuario declare sus gustos (subrubro + nivel), los vea y edite, para que el producto tenga la señal que hoy no existe.

**Architecture:** Nueva feature `interest/` (entidad, repositorio, servicio, controller, templates). Tabla `user_interests` con entidad intermedia porque la relación **tiene un atributo** (`level`): no es un `@ManyToMany`. Un flag en `users` distingue "nunca vio el paso" de "eligió cero". El catálogo (rubro → subrubros) ya existe en `Subrubro.categoria` y se agrupa contra el enum `ExperienceCategory`.

**Tech Stack:** Spring Boot 4.1.1, Thymeleaf, JPA/Hibernate (`ddl-auto=validate`), Flyway, H2 en tests, Tailwind por CDN + `app.css`, JS vanilla.

**Spec:** `docs/superpowers/specs/2026-10-22-subrubros-base.md` (catálogo y naming) y prototipo `features/.extract/pages/1-registro-intereses.html` + `2-registro-nivel-de-experiencia.html` (flujo de dos pasos y texto).

---

## Global Constraints

- **No ejecutar builds:** el agente NO corre `.\mvnw.cmd` (regla del repo). Cada paso "Run" lo ejecuta el usuario y confirma el resultado; el plan lo deja escrito para que sea reproducible.
- **Commits:** conventional commits en castellano, imperativo. Sin `Co-Authored-By` ni atribución de IA.
- **Migraciones:** una versión de Flyway no se edita una vez aplicada. `V4__subrubros.sql` ya existe y pudo aplicarse: todo lo nuevo va en `V5__user_interests.sql`.
- **Naming:** `UserInterest` / `user_interests` / `subrubro_id` / `interest` (paquete).
- **Categorías:** los strings de `subrubros.categoria` deben coincidir EXACTO con `ExperienceCategory` (`AVENTURA`, `GASTRONOMIA` sin acento, `CULTURAL`, `DEPORTIVA`, `SOCIAL`).
- **Tests:** `@SpringBootTest` con H2 compartida + Flyway + `ddl-auto=validate`; aislamiento con `@Transactional` o usuarios/nombres únicos. Unicidad con `saveAndFlush` para que salga `DataIntegrityViolationException`.
- **Idioma de UI:** castellano rioplatense, igual que el resto de las vistas.
- **Alcance cerrado:** NO ranking/recomendación (B2/B3), NO filtro del feed por subrubro (A), NO asignación de subrubro en el form del creador (C), NO el eje "¿Qué buscas en Shared?".

## Review Focus

- **Entrada directa a `/feed`** (PWA, bookmark, botón atrás) con flag en `false`: debe gatearse igual, sin loop de redirect.
- **Usuario sin JS**: el selector tiene que seguir siendo usable y enviable (fallback `<noscript>`), porque el gate bloquea el feed.
- **Subrubro inactivo o borrado entre el paso 1 y el paso 2**: no debe tirar 500; debe rechazar con mensaje y volver al paso 1.
- **Doble submit del paso 2**: no debe duplicar filas ni romper por el índice único.
- **Noveno interés**: el mensaje tiene que ser claro y no perder lo ya elegido.

---

## 0. Decisiones

- **D1 - Modelo:** `user_interests (id, user_id, subrubro_id, level, created_at)` con `UNIQUE (user_id, subrubro_id)`, `ON DELETE CASCADE` en `user_id`.
- **D2 - Nivel:** enum `InterestLevel { CURIOSO, AFICIONADO, EXPERTO }`, default `CURIOSO`. **No bloquea**: el paso 2 arranca con `CURIOSO` preseleccionado.
- **D3 - Flag:** `users.interests_onboarded BOOLEAN NOT NULL DEFAULT FALSE`. La migración lo pone en `TRUE` para las filas existentes (no molestar a quien ya usa la app).
- **D4 - Gate:** un único punto, `FeedController.feed()`. No se gatea `/feed/api` (JSON) ni el resto de la app.
- **D5 - Límites:** `MAX_INTERESES = 8`; cero intereses es válido (equivale a saltear).
- **D6 - Flujo en dos POST (sin sesión intermedia):**
  1. `GET /onboarding/intereses` → selector.
  2. `POST /onboarding/intereses` (`subrubroId` repetido) → render de niveles con los ids en hidden.
  3. `POST /onboarding/intereses/niveles` → guarda, marca flag, redirige.
  4. `POST /onboarding/intereses/saltear` → marca flag, redirige.
  - `next` (`feed` | `settings`) en los cuatro, con whitelist.
- **D7 - Edición:** desde Configuración, link a `/onboarding/intereses?next=settings`. Mismo flujo, pre-cargado.
- **D8 - UI:** página de onboarding con resumen de chips siempre visible + botón "Agregar interés" que abre un `<dialog>` con dos paneles (rubros → subrubros) y flecha atrás. Badge de conteo por rubro. "Seleccionar todos" por rubro. Footer sticky con `Continuar`.
- **D9 - Accesibilidad:** `<dialog>` con `showModal()`, `Esc` cierra, flecha atrás con `aria-label`, `aria-checked` en los toggles, targets ≥ 44px, contraste del chip activo igual al del feed, respeto de `prefers-reduced-motion`.

---

## 1. Archivos

Producción:

| Archivo | Rol |
|---|---|
| `src/main/resources/db/migration/V5__user_interests.sql` | Flag en `users` + tabla `user_interests` + índices |
| `src/main/java/com/openlab/mx/share/user/entity/User.java` | Campo `interestsOnboarded` |
| `src/main/java/com/openlab/mx/share/interest/entity/InterestLevel.java` | Enum de nivel |
| `src/main/java/com/openlab/mx/share/interest/entity/UserInterest.java` | Entidad intermedia con nivel |
| `src/main/java/com/openlab/mx/share/interest/repository/UserInterestRepository.java` | Consultas por usuario |
| `src/main/java/com/openlab/mx/share/interest/service/InterestService.java` | Catálogo agrupado + reglas de guardado |
| `src/main/java/com/openlab/mx/share/interest/dto/InterestRubro.java` | `record(rubro, label, subrubros)` |
| `src/main/java/com/openlab/mx/share/interest/dto/InterestLevelsForm.java` | Backing object del paso 2 |
| `src/main/java/com/openlab/mx/share/interest/controller/OnboardingController.java` | Los cuatro endpoints |
| `src/main/java/com/openlab/mx/share/interest/DemoInterestSeeder.java` | Intereses del usuario demo |
| `src/main/java/com/openlab/mx/share/experience/entity/ExperienceCategory.java` | `getLabel()` (solo agrega, no cambia `toString`) |
| `src/main/java/com/openlab/mx/share/experience/controller/FeedController.java` | Gate en `feed()` |
| `src/main/java/com/openlab/mx/share/shared/DataSeeder.java` | Demo users con flag `true` |
| `src/main/resources/templates/interest/onboarding.html` | Paso 1 + selector |
| `src/main/resources/templates/interest/levels.html` | Paso 2 |
| `src/main/resources/templates/profile/settings.html` | Sección "Intereses" |
| `src/main/java/com/openlab/mx/share/user/controller/ProfileController.java` | `intereses` en el modelo de `/settings` |
| `src/main/resources/static/js/interest-picker.js` | Paneles, conteos, límite, resumen |
| `src/main/resources/static/css/app.css` | Estilos `.interest-*` |
| `src/main/resources/templates/fragments/layout.html` | Cache-buster `app.css(v=19)` |

Tests:

| Archivo | Cubre |
|---|---|
| `src/test/java/com/openlab/mx/share/interest/UserInterestRepositoryTests.java` | Unicidad, lectura con subrubro, borrado por usuario |
| `src/test/java/com/openlab/mx/share/interest/InterestServiceTests.java` | Catálogo, límite, defaults, reemplazo del set, ids inválidos |
| `src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java` | Gate, paso 1, paso 2, skip, `next=settings` |
| `src/test/java/com/openlab/mx/share/interest/DemoInterestSeederTests.java` | Demo con intereses, segunda corrida no duplica |
| `src/test/java/com/openlab/mx/share/FeedAndFavoritesControllerTests.java` | Ajuste de `crearUsuarioSinCiudad()` (flag `true`) |

---

## 2. Contratos

```java
public enum InterestLevel { CURIOSO, AFICIONADO, EXPERTO }

public record InterestRubro(ExperienceCategory rubro, String label, List<Subrubro> subrubros) {}

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {
    @EntityGraph(attributePaths = "subrubro")
    List<UserInterest> findByUserId(Long userId);                 // lectura para UI
    boolean existsByUserIdAndSubrubroId(Long userId, Long subrubroId);
    void deleteByUserId(Long userId);
    long countByUserId(Long userId);
}

public class InterestService {
    public static final int MAX_INTERESES = 8;
    public List<InterestRubro> catalogo();                                  // 5 rubros, orden del enum, subrubros por nombre
    public List<UserInterest> intereses(User user);
    public List<Long> idsSeleccionados(User user);
    public Map<Long, InterestLevel> niveles(User user);
    public void guardar(User user, List<Long> subrubroIds, Map<Long, InterestLevel> niveles); // valida, reemplaza, marca flag
    public void marcarOnboarded(User user);
}
```

`OnboardingController` (todo bajo `.anyRequest().authenticated()`; no toca `SecurityConfig`):

```java
GET  /onboarding/intereses              -> "interest/onboarding"
POST /onboarding/intereses              -> "interest/levels"
POST /onboarding/intereses/niveles      -> redirect
POST /onboarding/intereses/saltear      -> redirect
```

---

## Task 1: Migración V5 + flag en `User`

**Files:**
- Create: `src/main/resources/db/migration/V5__user_interests.sql`
- Modify: `src/main/java/com/openlab/mx/share/user/entity/User.java`
- Test: `src/test/java/com/openlab/mx/share/interest/UserInterestRepositoryTests.java` (se completa en Task 2)

**Interfaces:**
- Consumes: `users` (V1 L17-32).
- Produces: `users.interests_onboarded`, tabla `user_interests`, `User.isInterestsOnboarded()/setInterestsOnboarded(boolean)`.

- [ ] **Step 1: Escribir la migración**

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS interests_onboarded BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET interests_onboarded = TRUE;

CREATE TABLE IF NOT EXISTS user_interests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subrubro_id BIGINT NOT NULL REFERENCES subrubros(id),
    level VARCHAR(20) NOT NULL DEFAULT 'CURIOSO',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_interest UNIQUE (user_id, subrubro_id)
);

CREATE INDEX IF NOT EXISTS idx_user_interests_user ON user_interests(user_id);
```

- [ ] **Step 2: Agregar el campo a `User`**

`@Column(name = "interests_onboarded", nullable = false) private boolean interestsOnboarded;` con getter/setter (Lombok ya usado en la clase).

- [ ] **Step 3: Verificar el arranque del contexto**

Run: `.\mvnw.cmd test -Dtest=ExperienceSubrubroTests -q`
Expected: PASS. Si falla con `missing column [interests_onboarded]`, la migración no se está aplicando (revisar `spring.flyway` en `src/test/resources/application.properties`).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V5__user_interests.sql src/main/java/com/openlab/mx/share/user/entity/User.java
git commit -m "feat(interests): agregar tabla user_interests y flag de onboarding"
```

---

## Task 2: Entidades `InterestLevel` + `UserInterest` + repositorio

**Files:**
- Create: `src/main/java/com/openlab/mx/share/interest/entity/InterestLevel.java`
- Create: `src/main/java/com/openlab/mx/share/interest/entity/UserInterest.java`
- Create: `src/main/java/com/openlab/mx/share/interest/repository/UserInterestRepository.java`
- Test: `src/test/java/com/openlab/mx/share/interest/UserInterestRepositoryTests.java`

**Interfaces:**
- Consumes: `User` (Task 1), `Subrubro`.
- Produces: `UserInterest` con `getUser()/setUser()`, `getSubrubro()/setSubrubro()`, `getLevel()/setLevel()`, `getCreatedAt()`; `UserInterestRepository`.

- [ ] **Step 1: Escribir el test que falla**

`UserInterestRepositoryTests` (`@SpringBootTest`, `@Transactional`):

```java
@Test void noPermiteRepetirElMismoSubrubroParaElMismoUsuario()
    // saveAndFlush del primero + saveAndFlush del duplicado
    // assertThrows(DataIntegrityViolationException.class, ...)

@Test void leeElSubrubroSinLazyInitializationException()
    // persistir con level EXPERTO, flush, em.clear(), findByUserId
    // assertEquals("Senderismo", interes.getSubrubro().getNombre())

@Test void deleteByUserIdBorraSoloLosDelUsuario()
    // dos usuarios con un interés cada uno; borrar uno; assertTrue(existsByUserIdAndSubrubroId del otro)
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=UserInterestRepositoryTests -q`
Expected: FAIL de compilación (`UserInterest` no existe).

- [ ] **Step 3: Implementar entidad y repositorio**

`UserInterest`: `@Entity @Table(name = "user_interests")`, `@Id @GeneratedValue`, `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "user_id")`, idem `subrubro`, `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) InterestLevel level`, `@Column(name = "created_at", nullable = false, updatable = false) Instant createdAt` inicializado en `@PrePersist` si es null.

Repositorio: la interfaz del contrato (`@EntityGraph` para evitar N+1 en la lectura).

- [ ] **Step 4: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=UserInterestRepositoryTests -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openlab/mx/share/interest src/test/java/com/openlab/mx/share/interest/UserInterestRepositoryTests.java
git commit -m "feat(interests): modelar UserInterest con nivel"
```

---

## Task 3: `InterestService` (catálogo + reglas)

**Files:**
- Create: `src/main/java/com/openlab/mx/share/interest/dto/InterestRubro.java`
- Create: `src/main/java/com/openlab/mx/share/interest/service/InterestService.java`
- Modify: `src/main/java/com/openlab/mx/share/experience/entity/ExperienceCategory.java`
- Test: `src/test/java/com/openlab/mx/share/interest/InterestServiceTests.java`

**Interfaces:**
- Consumes: `SubrubroRepository`, `UserInterestRepository`, `UserRepository`.
- Produces: el contrato de `InterestService` y `InterestRubro`.

- [ ] **Step 1: Escribir los tests que fallan**

```java
@Test void catalogoDevuelveLosCincoRubrosEnOrdenDelEnum()
    // assertEquals(List.of(AVENTURA, GASTRONOMIA, CULTURAL, DEPORTIVA, SOCIAL), catalogo.stream().map(InterestRubro::rubro).toList())
@Test void catalogoSoloIncluyeSubrubrosActivos()
    // un subrubro con activo=false no aparece en su rubro
@Test void catalogoIgnoraCategoriasDesconocidas()
    // fila con categoria "GASTRONOMÍA" (con acento) no rompe ni aparece
@Test void rechazaMasDeOchoIntereses()
    // 9 ids -> DomainException con mensaje "hasta 8"
@Test void nivelNuloQuedaEnCurioso()
    // guardar con Map vacío -> level CURIOSO
@Test void guardarReemplazaElSetAnterior()
    // guardar [A, B] y después [C] -> countByUserId == 1 y es C
@Test void guardarSinIdsDejaElUsuarioSinIntereses()
    // guardar con lista vacía -> countByUserId == 0
@Test void rechazaSubrubroInexistenteOInactivo()
    // id 999999 y subrubro con activo=false -> DomainException
@Test void guardarMarcaElFlagDeOnboarding()
    // after guardar -> user.isInterestsOnboarded() == true
@Test void idsDuplicadosNoDuplicanFilas()
    // guardar [A, A] -> countByUserId == 1
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=InterestServiceTests -q`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

- `ExperienceCategory.getLabel()`: `AVENTURA -> "Aventura"`, `GASTRONOMIA -> "Gastronomía"`, resto igual con tilde donde corresponda. **No cambiar `toString()`.**
- `catalogo()`: recorrer `ExperienceCategory.values()`; filtrar subrubros por `categoria == rubro.name()` y `activo`; ordenar por `nombre`; parsear con `ExperienceCategory.valueOf` y saltar (con `log.warn`) las filas cuya categoría no matchea. Devuelve siempre los 5 rubros, aunque un rubro quede sin subrubros.
- `guardar(...)`: `@Transactional`. Deduplicar ids; rechazar `> MAX_INTERESES`; cargar todos con `findAllById` y verificar que existan y estén activos; `deleteByUserId`; insertar con nivel `niveles.getOrDefault(id, CURIOSO)`; setear el flag y guardar el usuario.

- [ ] **Step 4: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=InterestServiceTests -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openlab/mx/share/interest src/main/java/com/openlab/mx/share/experience/entity/ExperienceCategory.java src/test/java/com/openlab/mx/share/interest/InterestServiceTests.java
git commit -m "feat(interests): servicio de catalogo y guardado de intereses"
```

---

## Task 4: Gate en el feed

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/experience/controller/FeedController.java:28-58`
- Modify: `src/test/java/com/openlab/mx/share/FeedAndFavoritesControllerTests.java` (helper `crearUsuarioSinCiudad`)
- Test: `src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java` (los casos de gate)

**Interfaces:**
- Consumes: `User.isInterestsOnboarded()` (Task 1).
- Produces: comportamiento de redirect; base para el controller de Task 5.

- [ ] **Step 1: Escribir los tests que fallan**

```java
@Test @WithMockUser("demo_user")
void feedNoGateaSiElUsuarioYaVioElOnboarding() // demo_user tiene flag true -> 200

@Test
void feedGateaAlUsuarioNuevo() // usuario nuevo -> 302 a /onboarding/intereses

@Test
void feedApiNoGatea() // usuario nuevo -> 200 JSON (no redirect)
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: FAIL en `feedGateaAlUsuarioNuevo` (hoy devuelve 200).

- [ ] **Step 3: Implementar el gate**

En `feed()`: después de `User user = currentUser.get();`

```java
if (user != null && !user.isInterestsOnboarded()) {
    return "redirect:/onboarding/intereses";
}
```

No tocar `feedApi()`.

- [ ] **Step 4: Ajustar los tests existentes que crean usuarios frescos**

En `FeedAndFavoritesControllerTests.crearUsuarioSinCiudad()` agregar `.interestsOnboarded(true)` al builder (esos tests verifican ubicación, no onboarding). Grep de control: todo test que cree un `User` y pegue a `/feed` debe setear el flag.

- [ ] **Step 5: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests,FeedAndFavoritesControllerTests -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openlab/mx/share/experience/controller/FeedController.java src/test/java/com/openlab/mx/share
git commit -m "feat(feed): redirigir al onboarding de intereses"
```

---

## Task 5: Controller del paso 1

**Files:**
- Create: `src/main/java/com/openlab/mx/share/interest/controller/OnboardingController.java`
- Create: `src/main/java/com/openlab/mx/share/interest/dto/InterestLevelsForm.java`
- Test: `src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java`

**Interfaces:**
- Consumes: `InterestService` (Task 3), `CurrentUser`.
- Produces: rutas y modelo de `onboarding.html` / `levels.html`.

- [ ] **Step 1: Escribir los tests que fallan**

```java
@Test void getOnboardingRenderizaElSelector()
    // 200, view "interest/onboarding", model "rubros" de tamaño 5, contiene "¿Qué te mueve"
@Test void getOnboardingPreseleccionaLosInteresesExistentes()
    // usuario con 2 intereses -> model "seleccionados" contiene los 2 ids
@Test void postOnboardingLlevaALosNiveles()
    // POST subrubroId=1&subrubroId=2 -> view "interest/levels", model con los 2 subrubros
@Test void postOnboardingSinSeleccionEquivaleASaltear()
    // POST sin ids -> flag true + redirect /feed
@Test void postOnboardingRespetaNextSettings()
    // POST ...&next=settings -> redirect /settings
@Test void postOnboardingRechazaNueveIntereses()
    // POST con 9 ids -> re-render del paso 1 con error que contiene "hasta 8"
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

- `GET /onboarding/intereses`: modelo `rubros` (`catalogo()`), `seleccionados` (`idsSeleccionados`), `niveles` (`niveles`), `max`, `next`, `paso=1`.
- `POST /onboarding/intereses`: si la lista viene vacía → `marcarOnboarded` + redirect; si > 8 → re-render con `error`; si no, cargar los `Subrubro` y renderizar `interest/levels` con `seleccionados`, `niveles`, `next`, `paso=2`.
- `next`: whitelist explícita (`"settings"` → `/settings`, cualquier otro → `/feed`).
- `InterestLevelsForm`: `List<Long> subrubroIds` y `Map<Long, InterestLevel> levels`, con getters/setters.

- [ ] **Step 4: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openlab/mx/share/interest src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java
git commit -m "feat(interests): paso 1 del onboarding con selector por rubro"
```

---

## Task 6: Paso 2 (niveles) + skip

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/interest/controller/OnboardingController.java`
- Test: `src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java`

**Interfaces:**
- Consumes: `InterestService.guardar(User, List<Long>, Map<Long, InterestLevel>)`, `InterestLevelsForm`.
- Produces: guardado definitivo.

- [ ] **Step 1: Escribir los tests que fallan**

```java
@Test void postNivelesGuardaYMarcaOnboarded()
    // POST subrubroIds=1&subrubroIds=2&levels[1]=EXPERTO&levels[2]=CURIOSO
    // -> 2 filas, nivel correcto, flag true, redirect /feed
@Test void postNivelesSinNivelUsaCurioso()
    // POST subrubroIds=1 sin levels -> level CURIOSO
@Test void postNivelesRechazaSubrubroInexistente()
    // subrubroIds=999999 -> re-render "interest/levels" con error, sin filas
@Test void postSaltearMarcaElFlagYNoGuardaIntereses()
    // POST /onboarding/intereses/saltear -> flag true, count 0, redirect /feed
@Test void postNivelesRespetaNextSettings()
    // ...&next=settings -> redirect /settings
@Test void dobleSubmitNoDuplica()
    // mismo POST dos veces -> countByUserId == 2 (no 4) sin excepción
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: FAIL (404 / sin persistencia).

- [ ] **Step 3: Implementar**

`POST /onboarding/intereses/niveles` con `@ModelAttribute InterestLevelsForm form`: llamar `interestService.guardar(...)`, capturar `DomainException` → re-render con `error`; si todo bien → redirect según `next`.
`POST /onboarding/intereses/saltear`: `marcarOnboarded` + redirect según `next`.

- [ ] **Step 4: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openlab/mx/share/interest src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java
git commit -m "feat(interests): paso 2 de niveles y guardado"
```

---

## Task 7: Templates + JS + CSS

**Files:**
- Create: `src/main/resources/templates/interest/onboarding.html`
- Create: `src/main/resources/templates/interest/levels.html`
- Create: `src/main/resources/static/js/interest-picker.js`
- Modify: `src/main/resources/static/css/app.css`
- Modify: `src/main/resources/templates/fragments/layout.html:17`
- Test: `src/test/java/com/openlab/mx/share/interest/OnboardingControllerTests.java`

**Interfaces:**
- Consumes: modelo de Task 5/6 (`rubros`, `seleccionados`, `niveles`, `max`, `next`, `paso`).
- Produces: HTML que los tests de render verifican.

- [ ] **Step 1: Escribir los tests de render que fallan**

```java
@Test void selectorMuestraRubrosYSubrubros()
    // contiene "Aventura", "Senderismo", 'data-rubro="AVENTURA"', "<dialog"
@Test void selectorTieneBotonVolverYContador()
    // contiene 'data-back', 'aria-label="Volver a los rubros"', "elegidos"
@Test void nivelesRenderizaTresOpcionesPorSubrubro()
    // por cada subrubro: inputs CURIOSO/AFICIONADO/EXPERTO, CURIOSO con checked
@Test void selectorIncluyeFallbackSinJs()
    // el HTML contiene "<noscript" y los checkboxes están dentro del <form> (enviables sin JS)
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: FAIL (no existe el template).

- [ ] **Step 3: Implementar `onboarding.html`**

- Header: "Paso 1 de 2", "¿Qué te mueve en tu ciudad?" + subtítulo del prototipo.
- Resumen: `#interest-summary` con chips de los seleccionados (nombre + nivel + botón quitar).
- Botón `#interest-add` ("+ Agregar interés") que abre `dialog#interest-sheet`.
- `<dialog id="interest-sheet">` dentro del `<form>`:
  - Panel rubros: 5 tarjetas con `data-rubro`, `data-target`, badge `[data-count-for]`.
  - Un panel por rubro (oculto) con: `button[data-back]` + nombre del rubro + "Seleccionar todos" + checkboxes `name="subrubroId"` con `data-name`.
- Footer sticky: contador de elegidos + "Continuar" (submit).
- `<noscript>`: CSS que hace visible el contenido del dialog y oculta el botón de abrir.

- [ ] **Step 4: Implementar `levels.html`**

- "Paso 2 de 2", "¿Qué tanto sabés de cada uno?" + subtítulo.
- Una fila por subrubro con segmented control de 3 radios `name="levels[<id>"` (CURIOSO `checked` por defecto) + hidden `name="subrubroIds"`.
- Botones: "Volver" (`history.back()` o link a `/onboarding/intereses?next=...`) y "Guardar".

- [ ] **Step 5: Implementar `interest-picker.js`**

Solo si existe `#interest-sheet`: abrir/cerrar con `showModal()`/`close()`, `Esc` nativo, mostrar panel del rubro tocado, volver con `data-back`, actualizar badges y resumen en cada `change`, bloquear el 9.º con mensaje inline (revertir el check), "Seleccionar todos" por rubro, y deshabilitar "Continuar" si hay 0 (o dejar pasar como skip según D5: **dejar pasar**).

- [ ] **Step 6: CSS + cache-buster**

Agregar bloque `.interest-*` a `app.css` (sheet, paneles, tarjetas de rubro, chip con conteo, segmented control) y subir `app.css(v=18)` → `app.css(v=19)` en `layout.html`.

- [ ] **Step 7: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=OnboardingControllerTests -q`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/interest src/main/resources/static/js/interest-picker.js src/main/resources/static/css/app.css src/main/resources/templates/fragments/layout.html
git commit -m "feat(interests): UI del selector por rubro y niveles"
```

---

## Task 8: Intereses en Configuración + datos demo

**Files:**
- Modify: `src/main/resources/templates/profile/settings.html`
- Modify: `src/main/java/com/openlab/mx/share/user/controller/ProfileController.java:41-44` (agregar `intereses` al modelo)
- Create: `src/main/java/com/openlab/mx/share/interest/DemoInterestSeeder.java`
- Modify: `src/main/java/com/openlab/mx/share/shared/DataSeeder.java:57-86`
- Modify: `src/main/java/com/openlab/mx/share/experience/SubrubroSeeder.java:9` (`@Order(1)`)
- Test: `src/test/java/com/openlab/mx/share/interest/DemoInterestSeederTests.java`

**Interfaces:**
- Consumes: `InterestService.intereses()`, `SubrubroRepository`, `UserRepository`.
- Produces: sección de configuración y datos demo.

- [ ] **Step 1: Escribir los tests que fallan**

```java
@Test void configuraciónMuestraLosInteresesConSuNivel()
    // GET /settings -> contiene "Intereses", el nombre del subrubro y "Experto"
@Test void configuraciónMuestraElLinkDeEdicion()
    // contiene href="/onboarding/intereses?next=settings"
@Test void demoUserTieneCuatroIntereses()
    // countByUserId(demo_user) == 4
@Test void seederEsIdempotente()
    // correr run() dos veces -> sigue en 4
@Test void seederNoRompeSiElSubrubroNoExiste()
    // no lanza excepción y no inserta
```

- [ ] **Step 2: Correr y ver el fallo**

Run: `.\mvnw.cmd test -Dtest=DemoInterestSeederTests -q`
Expected: FAIL de compilación.

- [ ] **Step 3: Implementar**

- `settings.html`: sección `card` "Intereses" con chips `nombre · nivel`, empty state ("Todavía no elegiste tus gustos") y link "Editar" a `/onboarding/intereses?next=settings`. Requiere que `ProfileController` (GET `/settings`) agregue `intereses` al modelo.
- `DemoInterestSeeder` (`CommandLineRunner`, `@Order(2)`): para `demo_user`, si `countByUserId == 0`, buscar por `(categoria, nombre)` y crear 4: `Senderismo` (AFICIONADO), `Cata de cafés` (CURIOSO), `Museo/arqueología` (CURIOSO), `Meetups/lenguas` (EXPERTO). Si un subrubro no existe, saltearlo.
- `SubrubroSeeder`: `@Order(1)` para garantizar el orden de los runners.
- `DataSeeder`: `.interestsOnboarded(true)` en ambos demo users.

- [ ] **Step 4: Correr y ver el verde**

Run: `.\mvnw.cmd test -Dtest=DemoInterestSeederTests -q`
Expected: PASS.

- [ ] **Step 5: Correr la suite completa**

Run: `.\mvnw.cmd test -q`
Expected: PASS. Baseline 311 tests; la suite crece con los nuevos.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/profile/settings.html src/main/java/com/openlab/mx/share/interest src/main/java/com/openlab/mx/share/shared/DataSeeder.java src/main/java/com/openlab/mx/share/experience/SubrubroSeeder.java
git commit -m "feat(interests): intereses en configuracion y datos demo"
```

---

## 3. Riesgos conocidos

- **Redirect loop:** `/onboarding/intereses` no está gateado y el gate solo mira `/feed`. Verificado por diseño; el test `feedGateaAlUsuarioNuevo` fija el destino.
- **Checksum de Flyway:** `V4` ya se editó dos veces y pudo aplicarse en dev. Si `mvnw` falla con checksum mismatch, mover el cambio a `V5`/`V6` — nunca editar la versión aplicada.
- **`@Order` entre `CommandLineRunner`:** sin orden explícito, `DemoInterestSeeder` puede correr antes de `SubrubroSeeder` y no encontrar nada. Por eso `@Order` + skip si falta el subrubro.
- **H2 vs Postgres:** `ON DELETE CASCADE` e índice único funcionan en ambos; `now()` es `current_timestamp` en H2 (compatible).
- **`ddl-auto=validate`:** cualquier desvío entre entidad y migración rompe TODOS los `@SpringBootTest` de una (ya pasó con `subrubro_id`). Por eso Task 1 valida el contexto antes de seguir.
