# AI Proposal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pantalla de propuesta de experiencia con IA (Spring AI, proveedor conmutable por env, stub sin key) que persiste borradores editables y prellena el form de alta existente.

**Architecture:** Puerto `ProposalGenerator` con dos implementaciones (stub determinista / ChatClient de Spring AI) seleccionadas por `ProposalConfig` según env; controller Thymeleaf propio `/onboarding/propuesta` (PRG, whitelist `next`) gatea en `/feed` como segundo gate; borradores en tabla nueva `experience_drafts`; el prefill entra por `?draft=` en `/experiences/new`.

**Tech Stack:** Spring Boot 4.1.1, Java 25, `spring-ai-bom` **2.0.2** (starters google-genai/openai/ollama), Thymeleaf, Flyway (Postgres + H2 sin modo), MockMvc + Mockito, Playwright (e2e).

**Spec:** `docs/superpowers/specs/2026-10-26-ai-proposal-design.md` (el plan argumenta desde el spec; el spec viaja con el plan).

## Global Constraints

- `spring-ai-bom` **2.0.2** (Maven Central, última GA de la línea 2.0); starters: `spring-ai-starter-model-google-genai`, `spring-ai-starter-model-openai`, `spring-ai-starter-model-ollama`.
- Env de switch: `AI_CHAT_PROVIDER` → `spring.ai.model.chat`, **default `none`** (local/tests arrancan sin key). Otros env: `AI_PROPOSAL_PROVIDER` (`auto|stub`), `GEMINI_API_KEY`, `OPENAI_API_KEY`, `OLLAMA_BASE_URL`, `GEMINI_MODEL`, `OPENAI_MODEL`, `OLLAMA_MODEL`.
- Whitelist `next`: `feed` (default) | `new` — nunca redirigimos a un valor crudo.
- Límites del bloque de contenido: `name`/`description` `@NotBlank`; `includes`/`bring` ≤ 2000 chars; `textoLibre` truncado a **1000** chars en `ProposalService.generar` (decisión del plan: el spec no lo fija).
- Semántica de borradores: `generar` = **siempre INSERT**; `crear` = UPDATE del `draftId` propio (INSERT si vacío/ajeno); `GET` sin `?draft` muestra el **último** (`findByUserIdOrderByUpdatedAtDesc`); `?draft=abc` o ajeno = **se ignora, nunca 400**.
- Copy exacto de la pantalla: h1 "Tu propuesta de experiencia"; textarea label/placeholder "Contá qué te gustaría ofrecer"; botones "Generar propuesta", "Regenerar", "Crear experiencia", "Saltear"; error de generación "No pudimos generar la propuesta. Probá de nuevo." — tras el error, el **mismo botón "Generar propuesta" hace de Reintentar** (no hay botón extra); link en form.html: "Crear con IA".
- UI: clases existentes (`.card`, `.section`, `.page-hero`, `.btn-primary`, `.btn-secondary`, `.hint`, `.error`, tokens `--space-*`), **sin `style=` inline, sin emojis**, SVG inline `currentColor` + `aria-hidden` si hace falta ícono; textarea con `<label>`; región de error con `aria-live="polite"`.
- Flyway: SQL portable Postgres + H2 (el H2 de tests no corre en `MODE=PostgreSQL`); `ddl-auto=validate`; nunca reescribir una migración ya aplicada.
- Commits en castellano, Conventional Commits con scope (`proposal`, `ui`, `data`, `e2e`), en `feature/v2`, uno por tarea terminada.
- Verificación de UI obligatoria en navegador real (AGENTS.md): al final, `cd e2e && npx playwright test` y `./mvnw test` (378 pruebas hoy) en verde.

## Review Focus

Condiciones que el spec no convierte en test de una tarea; cada una se ancla al task que posee el código:

1. **Usuario anónimo en `/onboarding/propuesta`** → esperado: redirect a `/login`, sin 500. Test en Task 5 (`anonimoSinSesionRedirigeALogin`).
2. **`?draft=abc` (no numérico)** en `/onboarding/propuesta` y en `/experiences/new` → esperado: pantalla/form normal con 200, nunca 400. Tests en Task 5 (`draftNoNumericoSeIgnora`) y Task 6 (`draftNoNumericoNoRompeElForm`).
3. **`/feed/cards` con `proposalOnboarded=false`** → esperado: 200 sin gate (el scroll infinito necesita el HTML). Test en Task 5 (`feedCardsNoSeGatea`).
4. **Respuesta de IA fuera de límites o categoría inválida** → esperado: 1 reintento → `ProposalGenerationException`; y generar fallido **no crea fila** en `experience_drafts`. Tests en Task 3 (`jsonInvalidoReintentaYFalla`, `categoriaInvalidaFalla`, `fueraDeLimitesFalla`) y Task 5 (`generarFallidoNoCreaBorrador`, con `@MockitoBean`).
5. **`textoLibre` de 100.000 chars** → esperado: truncado a 1000 antes de llamar al generator (ni el stub ni la IA lo ven gigante). Test en Task 4 (`truncaTextoLibreALos1000Chars`).

---

### Task 1: Esquema V9 + modelo de borradores + flag de onboarding

**Files:**
- Create: `src/main/resources/db/migration/V9__ai_proposal.sql`
- Create: `src/main/java/com/openlab/mx/share/proposal/entity/ExperienceDraft.java`
- Create: `src/main/java/com/openlab/mx/share/proposal/repository/ExperienceDraftRepository.java`
- Modify: `src/main/java/com/openlab/mx/share/user/entity/User.java:82` (junto a `interestsOnboarded`)
- Modify: `src/main/java/com/openlab/mx/share/shared/DataSeeder.java:72` y `:87` (junto a `interestsOnboarded(true)`)
- Test: `src/test/java/com/openlab/mx/share/proposal/ExperienceDraftRepositoryTests.java`

**Interfaces:**
- Produces: `ExperienceDraft` (id, user, name, description, category `ExperienceCategory`, includes, bring, createdAt, updatedAt con setter), `ExperienceDraftRepository extends JpaRepository<ExperienceDraft, Long>` con `Optional<ExperienceDraft> findTopByUserIdOrderByUpdatedAtDesc(Long userId)` y `List<ExperienceDraft> findByUserIdOrderByUpdatedAtDesc(Long userId)`, `User.isProposalOnboarded()`/`setProposalOnboarded(boolean)` (default `false`).

- [ ] **Step 1: Escribir el test fallido**

```java
@SpringBootTest
class ExperienceDraftRepositoryTests {
    @Autowired ExperienceDraftRepository repository;
    @Autowired UserRepository usuarios; // patrón de los repositorios existentes

    @Test void guardaYDevuelveElUltimoBorradorDelUsuario() {
        User user = ...; // crear/guardar igual que los otros @SpringBootTest
        ExperienceDraft viejo = repository.save(draft(user, "Viejo")); // updatedAt = now-1min vía setter
        ExperienceDraft nuevo = repository.save(draft(user, "Nuevo"));
        assertEquals("Nuevo", repository.findTopByUserIdOrderByUpdatedAtDesc(user.getId()).orElseThrow().getName());
        assertEquals(2, repository.findByUserIdOrderByUpdatedAtDesc(user.getId()).size());
    }
}
```

- [ ] **Step 2: Correr y verlo fallar**

Run: `./mvnw test -Dtest=ExperienceDraftRepositoryTests`
Expected: FALLA (no existe la entity/tabla: `Schema-validation: missing table [experience_drafts]` o error de compilación).

- [ ] **Step 3: Implementar V9 + entity + repository + flag**

`V9__ai_proposal.sql` exacto del spec §5 (ALTER de `users.proposal_onboarded` **sin** UPDATE de backfill + `CREATE TABLE IF NOT EXISTS experience_drafts` + índice). Entity: `@Table(name="experience_drafts")`, `category` como `@Enumerated(EnumType.STRING)`, `includes`/`bring` con `@Column(columnDefinition="TEXT")` análogo a `Experience.includes`, `@PrePersist`/`@PreUpdate` que fijan `updatedAt`. `User`: `private boolean proposalOnboarded = false;` con getter/setter del patrón de `interestsOnboarded`. DataSeeder: `.proposalOnboarded(true)` en los dos usuarios.

- [ ] **Step 4: Correr y verlo pasar**

Run: `./mvnw test -Dtest=ExperienceDraftRepositoryTests` → PASS; después `./mvnw test` completo → **378+ en verde** (la migración corre en H2 de todos los tests y `ddl-auto=validate` acepta la entity).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V9__ai_proposal.sql src/main/java/com/openlab/mx/share/proposal src/main/java/com/openlab/mx/share/user/entity/User.java src/main/java/com/openlab/mx/share/shared/DataSeeder.java src/test/java/com/openlab/mx/share/proposal
git commit -m "feat(proposal): añadir borradores de propuesta y el flag de onboarding"
```

---

### Task 2: Puerto `ProposalGenerator` + DTOs + stub determinista

**Files:**
- Create: `proposal/generator/ProposalGenerator.java`, `proposal/generator/StubProposalGenerator.java`, `proposal/generator/ProposalGenerationException.java`
- Create: `proposal/dto/ProposalRequest.java`, `proposal/dto/InterestItem.java`, `proposal/dto/ExperienceProposal.java` (paquete `com.openlab.mx.share.proposal.*`)
- Test: `src/test/java/com/openlab/mx/share/proposal/StubProposalGeneratorTest.java`

**Interfaces:**
- Produces (lo consumen Tasks 3, 4 y 5):

```java
public interface ProposalGenerator { ExperienceProposal generate(ProposalRequest request); }
public record InterestItem(String rubroLabel, String subrubroNombre, InterestLevel nivel) {}
public record ProposalRequest(List<InterestItem> intereses, String textoLibre) {}
public record ExperienceProposal(String name, String description, ExperienceCategory category, String includes, String bring) {}
public class ProposalGenerationException extends DomainException { public ProposalGenerationException(String m) { super(m); } }
```

- [ ] **Step 1: Escribir el test fallido** — `StubProposalGeneratorTest` (JUnit 5 puro, sin contexto):

```java
@Test void devuelvePropuestaDeterministica()  // misma request dos veces → assertEquals(p1, p2)
@Test void respetaLimitesDelForm()            // name != blank && name.length() <= 255;
                                              // includes/bring != null && length() <= 2000;
                                              // category == rubro del interés (GASTRONOMIA del InterestItem de prueba)
@Test void incluyeElTextoLibreEnLaDescripcion() // textoLibre "Mezcla de cocina callejera" → description lo contiene
@Test void funcionaSinIntereses()             // lista vacía → propuesta válida igual (name/description no blank)
```

Request de prueba: `new ProposalRequest(List.of(new InterestItem("Gastronomía", "Tacos al pastor", InterestLevel.CURIOSO)), "Mezcla de cocina callejera")`.

- [ ] **Step 2:** Run `./mvnw test -Dtest=StubProposalGeneratorTest` → FALLA (no compila: clases inexistentes).
- [ ] **Step 3: Implementar** los 4 tipos + stub: templates fijos en español que componen `name` a partir del primer subrubro (o "Experiencia nueva" sin intereses), `description` = frase base + `textoLibre` verbatim si viene, `category` = rubro del interés 1 (`ExperienceCategory` del `rubroLabel`/interés — el InterestItem ya lleva el contexto; si la lista está vacía, `CULTURAL` como default neutral), `includes`/`bring` con 2-3 ítems fijos ≤ 2000. Sin I/O. `StubProposalGenerator` sin dependencias (constructor vacío).
- [ ] **Step 4:** Run `./mvnw test -Dtest=StubProposalGeneratorTest` → PASS.
- [ ] **Step 5: Commit** — `feat(proposal): añadir el puerto ProposalGenerator con stub determinista`.

---

### Task 3: Dependencias Spring AI + `ProposalConfig` + `AiProposalGenerator`

**Files:**
- Modify: `pom.xml` (dependencyManagement + 3 starters), `src/main/resources/application.properties` (bloque del spec §4.5)
- Create: `proposal/config/ProposalConfig.java`, `proposal/generator/AiProposalGenerator.java`
- Test: `src/test/java/com/openlab/mx/share/proposal/AiProposalGeneratorTest.java`, `.../ProposalConfigTest.java`

**Interfaces:**
- Consumes: `ProposalGenerator`/`ProposalRequest`/`ExperienceProposal`/`ProposalGenerationException` (Task 2).
- Produces: bean `ProposalGenerator` único en el contexto (stub o IA) — es lo que inyecta el `ProposalService` de Task 4.

- [ ] **Step 1: Dependencias + properties**

`pom.xml`: importar `org.springframework.ai:spring-ai-bom:2.0.2` en `dependencyManagement` + los 3 starters. `application.properties`: bloque completo del spec §4.5 (default `spring.ai.model.chat=${AI_CHAT_PROVIDER:none}`).
Run: `./mvnw test` → **verde con las 378** — esto ES la prueba de que los 3 starters en classpath + `none` arrancan sin key (si falla acá, el enfoque entero de degradación está mal y se detiene el plan).

- [ ] **Step 2: Verificar prefijos de OpenAI/Ollama contra la doc 2.0**

Leer `https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html` y `.../ollama-chat.html`. Expected: existen `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model`, `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model`. Si la doc 2.0 los renombró, ajustar el bloque de properties (los de Google GenAI ya están verificados en el spec §12). Dejar constancia en el mensaje del commit.

- [ ] **Step 3: Test fallido de `AiProposalGeneratorTest`** (Mockito deep stubs, sin red):

```java
ChatClient client = Mockito.mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
// JSON válido: when(chain.call().entity(ExperienceProposal.class)).thenReturn(propuestaValida)
@Test void jsonValidoDevuelvePropuesta()
@Test void jsonInvalidoReintentaYFalla()   // entity() lanza 1ª vez y 2ª vez → ProposalGenerationException; verify(entity, times(2))
@Test void categoriaInvalidaFalla()        // devuelve category null o desconocida → reintento → excepción
@Test void fueraDeLimitesFalla()           // description de 5000 chars → excepción (validación post-parse)
```

Run → FALLA (no compila).

- [ ] **Step 4: Implementar `AiProposalGenerator(ChatClient chatClient)`**

Firma: `public ExperienceProposal generate(ProposalRequest request)`. Construye prompt: `system(SISTEMA)` (copy del spec §4.3, en español) + `user(...)` con intereses y texto; `.call().entity(ExperienceProposal.class)` dentro de un bucle de 2 intentos; después valida (`name`/`description` no blank y ≤ límites, `category != null`, `includes`/`bring` ≤ 2000) y devuelve; si ambos intentos fallan (excepción del cliente **o** validación) → `ProposalGenerationException("propuesta inválida del modelo")`. Un `log.info` con ok/error y ms. *Si `.entity(Class)` no existe en Spring AI 2.0*, fallback: `.call().content()` + `ObjectMapper.readValue(..., ExperienceProposal.class)` (el mismo test lo debe dejar pasar). Run → PASS.

- [ ] **Step 5: Test fallido de `ProposalConfigTest`** (unit, `MockEnvironment` + `StaticListableBeanProvider` como `ObjectProvider<ChatClient.Builder>`):

```java
provider=none (default) sin builder          → instanceOf StubProposalGenerator
google-genai con GEMINI_API_KEY y builder     → instanceOf AiProposalGenerator
google-genai SIN key y builder                → instanceOf StubProposalGenerator   // degradación clave
ollama sin key con base-url y builder         → instanceOf AiProposalGenerator      // Ollama no necesita key
ai.proposal.provider=stub con key y builder   → instanceOf StubProposalGenerator
cualquier caso SIN builder                    → instanceOf StubProposalGenerator
```

Run → FALLA.

- [ ] **Step 6: Implementar `ProposalConfig`**

```java
@Bean
ProposalGenerator proposalGenerator(Environment env, ObjectProvider<ChatClient.Builder> builders)
```

Lógica: si `ai.proposal.provider=stub` → stub. Si `spring.ai.model.chat` vacío/`none` → stub. Si proveedor con key requerida (`google-genai`→`spring.ai.google.genai.api-key`; `openai`→`spring.ai.openai.api-key`) y key vacía → stub + `log.warn`. Si `builders.getIfAvailable()` es null → stub + `log.warn`. Si no → `new AiProposalGenerator(builder.build())`.

- [ ] **Step 7:** `./mvnw test -Dtest='AiProposalGeneratorTest,ProposalConfigTest'` → PASS; `./mvnw test` completo → verde; **Commit**: `feat(proposal): integrar Spring AI con stub como degradación sin key`.

---

### Task 4: `ProposalService` (truncación, semántica de borradores, flag)

**Files:**
- Create: `proposal/service/ProposalService.java`, `proposal/dto/ProposalForm.java`
- Test: `src/test/java/com/openlab/mx/share/proposal/ProposalServiceTest.java`

**Interfaces:**
- Consumes: `ProposalGenerator`, `ExperienceDraftRepository`, `InterestService.intereses(user)`/`niveles(user)` (ya existentes), `UserRepository` (para guardar el flag).
- Produces (lo consume el controller de Task 5 y `ExperienceController` en Task 6):

```java
@Service
public class ProposalService {
    public ExperienceDraft generar(User user, String textoLibre);                  // arma intereses, trunca a 1000, llama generator, INSERT
    public ExperienceDraft guardar(User user, String draftParam, ProposalForm form); // UPDATE si draftParam es propio, si no INSERT
    public Optional<ExperienceDraft> borradorPropio(User user, String draftParam); // parsea String→Long a mano; inválido/ajeno → empty
    public Optional<ExperienceDraft> ultimoBorrador(User user);
    public void marcarOnboarded(User user);                                       // proposalOnboarded=true + save
}
public record ProposalForm(@NotBlank String name, @NotBlank String description,
        @NotNull ExperienceCategory category,
        @Size(max = 2000) String includes, @Size(max = 2000) String bring) {}
```

- [ ] **Step 1: Test fallido** — `ProposalServiceTest` (constructor manual con un fake generator que captura la `ProposalRequest` y un repo/repo doble; patrón de los tests de servicio puros del proyecto, p. ej. `FavoriteServiceTest`):

```java
@Test void truncaTextoLibreALos1000Chars()   // texto de 100.000 → request.textoLibre().length() == 1000
@Test void generarSinInteresesNoFalla()      // usuario sin intereses → request.intereses() vacía y draft creado
@Test void generarFallidoNoGuardaNada()      // fake lanza ProposalGenerationException → assertThrows y repository.save nunca llamado
@Test void guardarConDraftAjenoCreaFilaNueva() // draftParam de otro usuario → no updateea, inserta fila propia
@Test void borradorPropioIgnoraParametroInvalido() // draftParam "abc" → Optional.empty(), sin excepción
```

Run → FALLA (no compila).

- [ ] **Step 2: Implementar `ProposalService`** — `generar`: `textoLibre == null ? "" : textoLibre.substring(0, min(1000, len))`; `InterestItem` armado desde `interestService.intereses(user)` + `niveles(user)` (rubro label vía el `Subrubro`/`ExperienceCategory` existente); llama `generator.generate(...)` **antes** de cualquier `save` (si falla, no se persiste nada); entity nueva con `user`. `guardar`: parseo manual de `draftParam` (mismo criterio de `borradorPropio`), UPDATE solo si existe y es del usuario. Run → PASS. Commit: `feat(proposal): orquestar generación, borradores y flag en ProposalService`.

---

### Task 5: Pantalla `/onboarding/propuesta` + gate en `/feed` + churn de tests

**Files:**
- Create: `proposal/controller/ProposalController.java`, `src/main/resources/templates/proposal/proposal.html`
- Modify: `src/main/java/com/openlab/mx/share/experience/controller/FeedController.java:50-56` (segundo `if`, después del gate de intereses)
- Modify (churn): `src/test/java/com/openlab/mx/share/FeedAndFavoritesControllerTests.java:276` (sumar `.proposalOnboarded(true)` al builder que ya tiene `.interestsOnboarded(true)`); cualquier otro test que falle con redirect a `/onboarding/propuesta` — el criterio: donde el builder setea `interestsOnboarded(true)`, sumar `proposalOnboarded(true)` (los usuarios demo ya lo traen del DataSeeder)
- Test: `src/test/java/com/openlab/mx/share/proposal/ProposalControllerTests.java` (setup MockMvc idéntico a `OnboardingControllerTests`) y `.../FeedProposalGateTests.java`

**Interfaces:**
- Consumes: todo Task 4.
- Produces: rutas del spec §6.

- [ ] **Step 1: Tests fallidos de `ProposalControllerTests`** (todos con `.with(csrf())` y `.with(user(...))` salvo el de anónimo):

```java
getOnboardingPropuestaRenderizaInteresesYGenerador()      // 200, contiene "Tu propuesta de experiencia"
generarHacePrgYCreaFila()                                 // POST generar → redirect /onboarding/propuesta?draft=\d+ y fila nueva
generarConCeroInteresesCreaBorrador()                     // usuario sin intereses → 302 y fila
generarFallidoNoCreaBorrador()                            // @MockitoBean ProposalGenerator lanza excepción → redirect con flash error y count de filas == 0
crearValidaRedirigeAlFormConFlag()                        // POST crear (form válido) → redirect /experiences/new?draft=\d+ y user.isProposalOnboarded() == true
crearConNombreEnBlancoVuelveALaPantalla()                 // name vacío → 200, th:errors visible, sin fila
saltearPoneElFlagYRedirigeAlDestino()                     // POST saltear next=new → /experiences/new; sin next → /feed
nextMaliciosoCaeAlFeed()                                  // next=//evil.com → redirect /feed
draftNoNumericoSeIgnora()                                 // GET ?draft=abc → 200 (Review Focus 2)
anonimoSinSesionRedirigeALogin()                          // sin .with(user) → redirect /login (Review Focus 1)
```

Run → FALLA (no compila / 404).

- [ ] **Step 2: Tests fallidos de `FeedProposalGateTests`**

```java
conFlagFalseRedirigeALaPropuesta()     // usuario recién registrado → GET /feed → redirect /onboarding/propuesta
conFlagTrueMuestraElFeed()             // demo_user (seeder, flag true) → 200
feedCardsNoSeGatea()                   // usuario con flag false → GET /feed/cards → 200 (Review Focus 3)
```

Run → FALLA.

- [ ] **Step 3: Implementar controller + template + gate**

`ProposalController` (`@RequestMapping("/onboarding/propuesta")`, `currentUser.require()` en POST como `OnboardingController`): GET arma pantalla (`paso` de intereses vía `InterestService`, borrador por `borradorPropio`/`ultimoBorrador`, `next`, `form`, `posiblesCategorias = ExperienceCategory.values()`); `POST /generar` → try `service.generar` → `redirect:/onboarding/propuesta?next=…&draft={id}`, catch `ProposalGenerationException` → `redirect` al GET con flash `error`; `POST /crear` con `@Valid @ModelAttribute ProposalForm` + BindingResult (errores → vuelve a la pantalla 200 con `th:errors`) y `@RequestParam(name="draft", required=false) String draft` (hidden del form, lo consume `service.guardar`) → `guardar` + `marcarOnboarded` → `redirect:/experiences/new?draft={id}`; `POST /saltear` → flag → destino. Whitelist local `destino(next)`/`ruta(destino)` copiando `OnboardingController.destino()`. Template: hero + chips de intereses (hint si la lista está vacía) + textarea `<label>` + "Generar propuesta"; tras propuesta: los 5 campos editables (select de categorías con `label`, nunca el enum), región `aria-live="polite"` con el copy de error, "Regenerar" + "Crear experiencia" + "Saltear" — copy exacto de Global Constraints.

Gate en `FeedController`, después del existente:

```java
if (user != null && !user.isProposalOnboarded()) {
    return "redirect:/onboarding/propuesta";
}
```

- [ ] **Step 4:** `./mvnw test -Dtest='ProposalControllerTests,FeedProposalGateTests'` → PASS. Luego `./mvnw test` completo → **si aparecen tests rojos con `redirectedUrl` a `/onboarding/propuesta`, aplicar el churn** (criterio del Files list) y repetir hasta verde.
- [ ] **Step 5: Commit** — `feat(proposal): añadir la pantalla de propuesta con gate en el feed`.

---

### Task 6: Prefill `?draft=` en `/experiences/new` + link "Crear con IA"

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/experience/controller/ExperienceController.java:63-71` (`form(...)`)
- Modify: `src/main/resources/templates/experience/form.html` (link "Crear con IA", `th:href="@{/onboarding/propuesta(next='new')}"`, `btn-secondary`, junto al título/back)
- Test: `src/test/java/com/openlab/mx/share/experience/ExperienceControllerTests.java` (agregar casos)

**Interfaces:**
- Consumes: `ProposalService.borradorPropio(User, String)` (Task 4).
- Produces: `GET /experiences/new?draft={id}` prellenado.

- [ ] **Step 1: Tests fallidos** en `ExperienceControllerTests`:

```java
draftPropioPrecargaElContenido()     // insertar ExperienceDraft propio vía repository → GET /experiences/new?draft={id}
                                      // → 200 y el HTML contiene value="Nombre borrador" (name) y la descripción
draftAjenoIgnoraElPrefill()          // draft de otro usuario → 200 sin ese contenido
draftNoNumericoNoRompeElForm()       // ?draft=abc → 200 (Review Focus 2)
linkCrearConIaVisible()              // GET /experiences/new → contiene href="/onboarding/propuesta?next=new"
```

Run → FALLA.

- [ ] **Step 2: Implementar**: `form(@RequestParam(name = "draft", required = false) String draft, Model model)` — si `verificationEnabled` y no es CREATOR sigue el redirect de KYC **antes** (spec §6); después, `proposalService.borradorPropio(user, draft)` presente → copiar `name/description/category/includes/bring` sobre `emptyForm()`; el resto del método no cambia. Link en `form.html`. Run → PASS.
- [ ] **Step 3:** `./mvnw test` completo → verde. **Commit:** `feat(experience): prellenar el form desde un borrador de propuesta`.

---

### Task 7: E2E de punta a punta + verificación final

**Files:**
- Create: `e2e/tests/proposal.spec.ts`

**Interfaces:**
- Consumes: helpers de `e2e/tests/helpers.ts` (`login`, `BASE_URL`, `DEMO_PASSWORD`) y los patrones de registro/CSRF de `e2e/tests/global-setup.ts`.

- [ ] **Step 1: Escribir `proposal.spec.ts`** con estos casos (mismo estilo de los specs existentes: comentarios que explican el por qué, un worker, sin paralelo):

1. `onboarding-redirige-a-la-propuesta`: registra usuario nuevo vía API (POST `/register` con el CSRF de `GET /register`; campos exactos de `templates/auth/register.html`), login UI (`helpers.login(page, username)`), completa el onboarding de intereses **por UI** (check del primer rubro → Continuar → niveles default → guardar), llega a `/onboarding/propuesta`.
2. `generar-y-crear-prellena-el-form`: en la misma sesión — "Generar propuesta" (stub: instantáneo), editar nombre y descripción, "Crear experiencia" → assert URL `/experiences/new?draft=` y que el form muestra el nombre/descripción editados.
3. `saltear-no-vuelve-a-aparecer`: usuario nuevo distinto → hasta la pantalla → "Saltear" → `/feed`; recargar `/feed` → sigue en `/feed` (no redirige).
4. `entry-desde-crear-experiencia`: login demo_user → `/experiences/new` → click "Crear con IA" → pantalla → "Saltear" → de vuelta en `/experiences/new`.

- [ ] **Step 2: Correr el e2e completo**

Run: `cd e2e && npx playwright test`
Expected: **10 tests en verde** (los 6 actuales intactos + los 4 nuevos). Si los 6 actuales se rompen, la causa es el gate/seeder: revisar `DataSeeder` antes que los tests.

- [ ] **Step 3: Verificación final de la rama**

Run: `./mvnw test` → verde (378+); `graft build` → grafo refrescado.

- [ ] **Step 4: Commit** — `test(e2e): cubrir el flujo de propuesta con IA`.
