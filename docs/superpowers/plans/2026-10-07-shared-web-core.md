# Shared Etapa B (Núcleo + Comunicación) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir la app web Shared (Spring Boot + Thymeleaf): feed con scroll infinito, CRUD de experiencias, favoritos, reservas con escrow + comisión 5%, 3 casos de cancelación, notificaciones y chat STOMP.

**Architecture:** Monolito modular por feature bajo `com.openlab.mx.share` (user, experience, reservation, payment, favorite, notification, chat, shared). Cada paquete con `controller/service/repository/entity/dto`. Vistas Thymeleaf con fragments; JS nativo para feed infinito, Leaflet y STOMP. Jobs `@Scheduled` para los 3 flujos temporales.

**Tech Stack:** Spring Boot 4.1.1, Java 25, Spring Security (BCrypt, form login), WebSocket STOMP, JPA + H2 file-based, Thymeleaf, jakarta.validation en records, Lombok, Leaflet+OSM (CDN), CSS nativo mobile-first, PWA (manifest + service worker).

**Spec:** `docs/superpowers/specs/2026-10-07-shared-web-core-design.md`

## Global Constraints

- Java **25**, Spring Boot **4.1.1** (pom actual). Build: `export JAVA_HOME="/c/Program Files/Java/jdk-25.0.2" && ./mvnw test` (el JAVA_HOME del sistema apunta a JDK 21 y falla).
- DB: H2 **file-based** (`jdbc:h2:file:./data/share`), JPA estándar, columnas **TEXT** (nunca CLOB), sin SQL específico de H2 (compatibilidad futura Postgres).
- DTOs de entrada = **Java record** con `jakarta.validation` (`@Valid` en controller). Lombok solo en entidades (`@Getter/@Setter/@Builder/@NoArgsConstructor`).
- `@Transactional` **solo** en métodos de escritura multi-entidad puntuales (p.ej. liberar escrow + notificar); prohibido a nivel de clase/servicio completo.
- Imágenes: `MultipartFile → Base64 → TEXT` en entidad **separate** `ExperienceImage` (`LAZY`); feed consulta **solo la tapa** (`position=0`) vía `ExperienceFeedDTO`; `pageSize = 1`.
- Escrow: comisión **5%** (`0.05`). Liberación: **todos los confirmados evalúan** (estrella 1–5 + comentario) **o** pasan **2 días** desde `COMPLETED`. Reembolso Caso 1: **>24h = 100%**, **≤24h = 50%** (constante `partial-refund-rate` en properties). Casos 2 y 3: **100%**.
- Registro: **+18** (`birthDate`). Seed: 2 usuarios demo, ciudad **CDMX** (19.4326, -99.1332). Categorías: `AVENTURA, GASTRONOMIA, CULTURAL, DEPORTIVA, SOCIAL`.
- Design tokens CSS: `--color-primary: #E91E63`, `--color-secondary: #D81B60`, `--bg: #F8F9FA`, `--text: #212121`, `--star: #FFC107`, cards `border-radius:16px`, `box-shadow: 0 4px 12px rgba(0,0,0,.08)`.
- Commits frecuentes en inglés/convención `type: descripción`; cada task termina en verde y commiteado.

## Review Focus

Condiciones que el spec no prueba explícitamente pero que romperían la app; cada una con su test en el task dueño:

1. **Sobreventa de cupo** (2 reservas concurrentes al último cupo) → test de límite en `ReservationServiceTest` (Task 5) con cupo=1 y dos reservas secuenciales: la segunda lanza `CupoAgotadoException`.
2. **Pago cancelado que llega a `RELEASED`** (refund y release compitiendo) → test en `PaymentServiceTest` (Task 5): desde `REFUNDED_FULL`, `tryRelease()` es no-op y lanza `EscrowException`.
3. **No-propietario mutando recursos** → MockMvc en Tasks 3/6: usuario B recibe 403/redirect al editar/borrar experiencia de A; y **no-inscrito** no puede enviar mensaje al chat (Task 7, test STOMP rechazado).
4. **Feed sin resultados / fin de páginas** → `FeedServiceTest` (Task 4): página más allá del final devuelve `Page` vacío (no error) y feed con 0 experiencias renderiza empty-state.
5. **Jobs idempotentes** → tests en Task 6: correr dos veces la liberación a 2 días no paga dos veces (segunda llamada no-op); marcar `COMPLETED` una instancia ya `COMPLETED` o `CANCELADO` no cambia estado.

---

### Task 1: Base técnica — pom, layout, CSS tokens, PWA

**Files:**
- Modify: `pom.xml` (agregar `spring-boot-starter-security`, `spring-boot-starter-websocket`)
- Create: `src/main/resources/static/css/app.css`, `src/main/resources/static/js/sw-register.js`, `src/main/resources/static/manifest.webmanifest`, `src/main/resources/static/js/sw.js`, `src/main/resources/static/icons/icon-192.png`, `icon-512.png`
- Create: `src/main/resources/templates/fragments/layout.html` (head, header, nav de 5 íconos), `fragments/modal.html`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/openlab/mx/share/BaseLayoutTests.java`

**Interfaces:**
- Produces: fragmentos `layout :: head`, `layout :: nav(active)` que todos los templates usan; CSS con los tokens de Global Constraints; `sw.js` cachea solo `css/app.css` y `manifest.webmanifest` (network-first para el resto).
- application.properties: `spring.datasource.url=jdbc:h2:file:./data/share`, `spring.h2.console.enabled=true`, `partial-refund-rate=0.5`, `escrow.auto-release-days=2`, `escrow.commission-rate=0.05`.

- [ ] **Step 1: Escribir el test fallido** — `BaseLayoutTests`: `@WebMvcTest` (con Security) de `GET /` redirige a `/login` (200 con la página de login) y `GET /manifest.webmanifest` devuelve 200 con `application/manifest+json`.
- [ ] **Step 2: Correr y ver fallar** — `export JAVA_HOME="/c/Program Files/Java/jdk-25.0.2" && ./mvnw test -Dtest=BaseLayoutTests` → FAIL.
- [ ] **Step 3: Implementar** — agregar las 2 dependencias al pom; `app.css` con los tokens; `manifest.webmanifest` (`name: "Shared"`, `display: "standalone"`, `background_color: "#F8F9FA"`, `theme_color: "#E91E63"`); `sw.js` + `sw-register.js`; fragmentos de layout con nav de 5 íconos (Feed, Favoritos, Tus Experiencias, Notificaciones, Configuración); properties.
- [ ] **Step 4: Correr y ver pasar** — mismo comando → PASS.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat: base tecnica, layout, design tokens y PWA shell"`.

### Task 2: Usuario — registro +18, login Spring Security, seed

**Files:**
- Create: `user/entity/User.java`, `user/entity/Role.java` (enum USER, CREATOR), `user/dto/RegisterRequest.java`, `user/repository/UserRepository.java`, `user/service/UserService.java`, `user/controller/AuthController.java`, `user/controller/ProfileController.java`
- Create: `shared/config/SecurityConfig.java`, `shared/DataSeeder.java`
- Create: `templates/auth/login.html`, `templates/auth/register.html`
- Test: `user/service/UserServiceTest.java`, `user/AuthFlowTests.java`

**Interfaces:**
- `User`: `id, username, email, passwordHash, birthDate (LocalDate), avatarBase64 (TEXT), city, lat, lng, role, spPoints (int)`
- `UserService.register(RegisterRequest r) → User` lanza `MenorDeEdadException` si `Period.between(r.birthDate(), LocalDate.now()).getYears() < 18` y `DuplicateUsernameException` si existe.
- `RegisterRequest` record: `@NotBlank username, @Email @NotBlank email, @NotBlank @Size(min=8) password, @NotNull birthDate`.
- `SecurityConfig`: formLogin → `/login`, éxito → `/feed`, logout → `/login`, `/login`,`/register` públicos, todo lo demás autenticado, `PasswordEncoder = BCryptPasswordEncoder`.
- `DataSeeder`: `CommandLineRunner`, solo si `userRepository.count()==0` → `demo_creator/Clave1234!` (CREATOR), `demo_user/Clave1234!` (USER), ciudad CDMX (19.4326, -99.1332), avatar base64 de 1x1 px.

- [ ] **Step 1: Test unitario fallido** — `UserServiceTest.testRegistraUsuarioMayorDeEdad`, `testRechazaMenorDe18` (assertThrows `MenorDeEdadException` con `birthDate = 2015-01-01`), `testRechazaUsernameDuplicado`.
- [ ] **Step 2: Ver fallar** → FAIL (clases no existen).
- [ ] **Step 3: Implementar** entidades, repo, service con la regla +18.
- [ ] **Step 4: Ver pasar** → PASS.
- [ ] **Step 5: Test de integración fallido** — `AuthFlowTests`: `GET /login` 200; `POST /register` con menor → vuelve al form con mensaje de error en HTML; login `demo_user` → redirect `/feed`; `GET /feed` sin sesión → redirect `/login`.
- [ ] **Step 6: Ver fallar** → FAIL.
- [ ] **Step 7: Implementar** `AuthController` (GET/POST register, login), `SecurityConfig`, templates con `th:action` (CSRF), `DataSeeder`.
- [ ] **Step 8: Ver pasar** → PASS.
- [ ] **Step 9: Commit** — `feat: registro +18, login con spring security y seed de usuarios demo`.

### Task 3: Dominio de experiencias — entidades, instancias, CRUD, Leaflet

**Files:**
- Create: `experience/entity/{Experience, ExperienceImage, ExperienceSchedule, ExperienceInstance, RecurrenceType, ExperienceCategory}.java`
- Create: `experience/dto/{ExperienceFormRequest, ScheduleEntryRequest, ExperienceResponse}.java`, `experience/repository/{ExperienceRepository, ExperienceImageRepository, ExperienceScheduleRepository, ExperienceInstanceRepository}.java`
- Create: `experience/service/{ExperienceService, InstanceGenerationService}.java`, `experience/controller/ExperienceController.java`
- Create: `templates/experience/form.html`, `templates/experience/detail.html`, `static/js/map.js`
- Create: `shared/exception/{GlobalExceptionHandler, DomainException, NotOwnerException, CupoAgotadoException, MenorDeEdadException, EscrowException, InstancePastException}.java`
- Test: `experience/service/{ExperienceServiceTest, InstanceGenerationServiceTest}.java`, `experience/ExperienceControllerTests.java`

**Interfaces:**
- `Experience`: `id, creator (User), name, description, durationMinutes, minCapacity, maxCapacity, price (BigDecimal), category (ExperienceCategory), recurrenceType (UNIQUE|WEEKLY), meetingLat, meetingLng, status (ACTIVE|CANCELLED), ratingAverage (double)`.
- `InstanceGenerationService.generateFor(Experience e) → List<ExperienceInstance>`: UNIQUE → 1 instancia con la fecha/hora del schedule; WEEKLY → una por ocurrencia semanal dentro de `startRange..endRange` (rango definido en `ExperienceSchedule`: `dayOfWeek, startTime, endTime, startDate, endDate`).
- `ExperienceService.create(ExperienceFormRequest r, User creator) → Experience` (persiste imágenes como `ExperienceImage(position=i)` y genera instancias futuras); `update(Long id, ExperienceFormRequest r, User editor) → Experience` (valida autoría, regenera **solo** instancias futuras, conserva pasadas); `delete(Long id, User editor)` (solo dueño, lanza `NotOwnerException`).
- `ExperienceFormRequest` record: `@NotBlank name, @NotBlank description, @Min(1) durationMinutes, @Min(1) minCapacity, @Min(1) maxCapacity con validación maxCapacity >= minCapacity, @DecimalMin("0") price, @NotNull category, @NotNull recurrenceType, schedule entries, List<MultipartFile> images (1..5, image/*, máx 2MB c/u)`.
- Rutas: `GET /experiences/new`, `POST /experiences`, `GET /experiences/{id}/edit`, `POST /experiences/{id}`, `POST /experiences/{id}/delete`, `GET /experiences/{id}` (detalle: galería completa LAZY + Leaflet con marcador del punto de encuentro).

- [ ] **Step 1: Test unitario fallido** — `InstanceGenerationServiceTest`: `testGeneraUnaInstanciaParaUnicaOcasion` (UNIQUE fecha 2026-11-01 10:00 → 1 instancia con esa fecha), `testGeneraInstanciasSemanales` (WEEKLY lunes y miércoles, rango 2026-11-01..2026-11-15 → 4 instancias), `testAlEditarAgendaSoloRegeneraFuturas` (paso + futura; tras update conserva la pasada id intacta).
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar** entidades, repos, `InstanceGenerationService`.
- [ ] **Step 4: Ver pasar** → PASS.
- [ ] **Step 5: Test unitario fallido** — `ExperienceServiceTest`: `testCreatePersisteImagenesPosition0EsTapa`, `testUpdateRechazaSiNoEsDueno` (assertThrows `NotOwnerException`), `testDeleteRechazaSiNoEsDueno`.
- [ ] **Step 6: Ver fallar** → FAIL.
- [ ] **Step 7: Implementar** `ExperienceService` (transaccional solo en `create`/`update` por la escritura de imágenes+schedule+instancias).
- [ ] **Step 8: Ver pasar** → PASS.
- [ ] **Step 9: Test MockMvc fallido** — `ExperienceControllerTests`: `@WithMockUser` crea experiencia con multipart válido → redirect; sin sesión → `/login`; dueño ve form de edición, otro usuario en `GET /experiences/{id}/edit` recibe 403; validación inválida → 200 con errores en el form.
- [ ] **Step 10: Ver fallar** → FAIL.
- [ ] **Step 11: Implementar** controller + `form.html`/`detail.html` (Leaflet desde CDN, `map.js` con clic→lat/lng y auto-centrado con `navigator.geolocation`).
- [ ] **Step 12: Ver pasar** → PASS.
- [ ] **Step 13: Commit** — `feat: dominio de experiencias, generacion de instancias y crud con mapa`.

### Task 4: Feed infinito (proyección ligera) + Favoritos + Experiencias Vividas

**Files:**
- Create: `experience/dto/ExperienceFeedDTO.java`, `experience/repository/FeedRepository.java` (o `@Query` en ExperienceRepository), `experience/controller/FeedController.java`
- Create: `favorite/entity/Favorite.java`, `favorite/repository/FavoriteRepository.java`, `favorite/service/FavoriteService.java`, `favorite/controller/FavoriteController.java`
- Create: `templates/feed.html`, `templates/favorites/{favorites.html, lived.html}`, `static/js/feed-infinite.js`
- Test: `experience/service/FeedServiceTest.java`, `favorite/service/FavoriteServiceTest.java`, `FeedControllerTests.java`, `FavoriteControllerTests.java`

**Interfaces:**
- `ExperienceFeedDTO` record: `id, name, description, coverBase64, meetingPointLabel, durationMinutes, nextDate (LocalDate), price (BigDecimal), ratingAverage, favorite (boolean)`. **Nunca** incluye el arreglo de imágenes.
- `FeedService.page(String city, Double lat, Double lng, ExperienceCategory category, int page) → Page<ExperienceFeedDTO>` con `pageSize = 1`, orden: ciudad del usuario → resto por distancia → `nextDate` ascendente.
- `GET /feed` (HTML, primeras tarjetas), `GET /feed/api?category=&page=` → JSON de 1 tarjeta (la consume `feed-infinite.js` con `IntersectionObserver`).
- `FavoriteService.add(Long experienceId, User u)` / `remove(Long experienceId, User u)` (constraint único → `add` repetido es idempotente), `listFavorites(User u) → List<ExperienceFeedDTO>`, `listLived(User u) → List<LivedExperienceResponse>` (instancias `COMPLETED` con reservation del usuario + su rating/comentario).
- `POST /favorites/{experienceId}/add`, `POST /favorites/{experienceId}/remove` (modal de confirmación en cliente, PRG).

- [ ] **Step 1: Test unitario fallido** — `FeedServiceTest`: `testPageSizeEs1`, `testOrdenaCiudadActualPrimero` (CDMX antes que Guadalajara para coords CDMX), `testFiltraPorCategoria`, `testPaginaMasAllaDelFinalDevuelveVacia` (Review Focus #4), `testDtoContieneSoloLaTapa` (mock repo; el DTO no recibe las imágenes 2+).
- [ ] **Step 2: Ver fallar** → FAIL.
- [ ] **Step 3: Implementar** DTO + query con join a `ExperienceImage position=0` + servicio de feed.
- [ ] **Step 4: Ver pasar** → PASS.
- [ ] **Step 5: Test unitario fallido** — `FavoriteServiceTest`: `testAddEsIdempotente`, `testRemoveInexistenteNoLanza`, `testListLivedSoloCompletadas`.
- [ ] **Step 6: Ver fallar** → FAIL. **Step 7:** Implementar favoritos. **Step 8:** Ver pasar → PASS.
- [ ] **Step 9: Test MockMvc fallido** — `FeedControllerTests`: `GET /feed` 200 con la tarjeta renderizada (thymeleaf: nombre de la experiencia en el HTML); feed vacío renderiza empty-state (Review Focus #4); `GET /feed/api?page=99` → JSON vacío 200. `FavoriteControllerTests`: add/remove redirigen con PRG y requieren sesión.
- [ ] **Step 10: Ver fallar** → FAIL. **Step 11:** Implementar controllers, `feed.html`, `feed-infinite.js`, vistas de favoritos/vividas. **Step 12:** Ver pasar → PASS.
- [ ] **Step 13: Commit** — `feat: feed infinito con proyeccion ligera, favoritos y experiencias vividas`.

### Task 5: Reservas + checkout simulado + escrow + comisión + rating/comentarios

**Files:**
- Create: `reservation/entity/Reservation.java` (enum `ReservationStatus {PENDIENTE, CONFIRMADA, CANCELADO}`), `reservation/dto/{ReservationRequest, CheckoutRequest}.java`, `reservation/repository/ReservationRepository.java`, `reservation/service/ReservationService.java`, `reservation/controller/{ReservationController, CheckoutController}.java`
- Create: `payment/entity/Payment.java` (enum `PaymentStatus {HELD, RELEASED, REFUNDED_FULL, REFUNDED_PARTIAL}`), `payment/repository/PaymentRepository.java`, `payment/service/{EscrowService, EscrowReleaseService}.java`, `payment/controller/PaymentController.java`
- Create: `experience/entity/{Rating, Comment}.java`, repositorios, `experience/service/{RatingService}.java`, `experience/controller/RatingController.java`
- Create: `templates/checkout.html`, `templates/experience/picker-modal.html`, `templates/confirmation.html`
- Test: `reservation/service/ReservationServiceTest.java`, `payment/service/EscrowServiceTest.java`, `payment/service/EscrowReleaseServiceTest.java`, `ReservationFlowTests.java` (MockMvc encadenado)

**Interfaces:**
- `ReservationService.reserve(Long instanceId, int attendees, User u) → Reservation`: valida instancia futura, **cupo** (`available >= attendees`, si no `CupoAgotadoException` — ver Review Focus #1), crea `Reservation(PENDIENTE)` + `Payment(HELD, amount = price*attendees, commission = amount*0.05)`.
- `ReservationService.confirm(Long reservationId, User creator) → Reservation` (solo dueño de la experiencia → `CONFIRMADA`; dispara notificación — Task 6 la consumirá).
- `CheckoutController`: `GET /checkout/{instanceId}` form de tarjeta simulada (number/expiry/cvv **no validados**), `POST /checkout/{instanceId}` con `CheckoutRequest(@NotBlank cardNumber pattern \\d{12,19}, @NotBlank holder)` → PRG a `/confirmation`.
- `EscrowService.tryRelease(Long reservationId)`: solo si `Reservation=CONFIRMADA` e instancia `COMPLETED` y **todos** los confirmados evalúan (rating+comment) → `Payment=RELEASED`, paga `amount − commission` al creador, notificación `PAGO_LIBERADO`. Si `Payment` ya no está `HELD` → `EscrowException` (Review Focus #2).
- `EscrowReleaseService.releaseIfDue(Long instanceId)`: instancias `COMPLETED` con `heldAt + 2 días <= now` → `tryRelease` (idempotente: segunda corrida no-op — Review Focus #5).
- `RatingService.rate(Long instanceId, User u, int stars, String text)`: 1..5, solo `CONFIRMADA` y `COMPLETED`, promedio en `Experience.ratingAverage`, después llama `EscrowService.tryRelease`.
- Rutas: `POST /reservations/{instanceId}` (abre picker/checkout), `POST /reservations/{id}/confirm` (creador), `POST /instances/{id}/rate`.

- [ ] **Step 1: Test unitario fallido** — `ReservationServiceTest`: `testReservaHappyPathCreaPaymentHeld` (assert monto y `commission == amount*0.05`), `testRechazaCupoAgotado` (cupo=1, 2ª reserva → `CupoAgotadoException` — Review Focus #1), `testRechazaInstanciaPasada`, `testConfirmSoloElDueno`.
- [ ] **Step 2: Ver fallar** → FAIL. **Step 3:** Implementar entidades+service. **Step 4:** Ver pasar → PASS.
- [ ] **Step 5: Test unitario fallido** — `EscrowServiceTest`: `testLiberaCuandoTodosEvaluan` (2 confirmados, ambos ratean → RELEASED y payout = amount*0.95), `testNoLiberaSiFaltaEvaluacion`, `testLiberaPasan2Dias` (reloj con `Instant` inyectable), `testReembolsoCanceladoNoSeLibera` (REFUNDED_FULL → tryRelease lanza `EscrowException` — Review Focus #2), `testLiberacionIdempotente` (2ª llamada no-op — Review Focus #5).
- [ ] **Step 6: Ver fallar** → FAIL. **Step 7:** Implementar `EscrowService`/`EscrowReleaseService` (`@Transactional` solo en `tryRelease`). **Step 8:** Ver pasar → PASS.
- [ ] **Step 9: Test unitario fallido** — `RatingServiceTest` (nombre de clase real: `RatingServiceTest`): `testRateRechazaFueraDeRango` (0 y 6 → `IllegalArgumentException`), `testRateActualizaPromedio`, `testRateDisparaLiberacion`.
- [ ] **Step 10: Ver fallar** → FAIL. **Step 11:** Implementar rating+comentarios. **Step 12:** Ver pasar → PASS.
- [ ] **Step 13: Test MockMvc fallido** — `ReservationFlowTests`: flujo encadenado con sesión `demo_user`: reservar → checkout 200 → POST checkout → 302 a `/confirmation` con "PAGASTE" en el HTML; sin sesión → `/login`.
- [ ] **Step 14: Ver fallar** → FAIL. **Step 15:** Implementar controllers + `checkout.html` + `confirmation.html` (degradado rosa `#E91E63`, texto "PAGASTE $X" / "COBRASTE $X"). **Step 16:** Ver pasar → PASS.
- [ ] **Step 17: Commit** — `feat: reservas, checkout simulado, escrow con comision 5% y ratings`.

### Task 6: Cancelaciones (3 casos) + notificaciones + jobs

**Files:**
- Create: `notification/entity/Notification.java` (enum `NotificationType {CANCELACION_REEMBOLSO, RESERVA_CONFIRMADA, SOLICITUD_RESERVA, PAGO_LIBERADO}`), `notification/repository/NotificationRepository.java`, `notification/service/NotificationService.java`, `notification/controller/NotificationController.java`, `templates/notifications/notifications.html`
- Create: `reservation/service/CancellationService.java`, `shared/jobs/{CompletionJob, MinCapacityJob, EscrowReleaseJob}.java` (o un solo `shared/jobs/ScheduledJobs.java` con 3 métodos)
- Modify: `templates/reservations/my-reservations.html` (botón Cancelar), `templates/experience/detail.html` (botón cancelar fecha para dueño)
- Test: `reservation/service/CancellationServiceTest.java`, `notification/service/NotificationServiceTest.java`, `shared/jobs/ScheduledJobsTest.java`, `CancellationFlowTests.java`

**Interfaces:**
- `CancellationService.cancelByParticipant(Long reservationId, User u)`: `Reservation=CANCELADO`, cupo +1 inmediato, notificación al creador, reembolso (**>24h: 100%; ≤24h: `partial-refund-rate`** → `Payment=REFUNDED_FULL/REFUNDED_PARTIAL`), **removido del chat** (borra su membership — Task 7 lo consultará vía `Reservation`), validando que sea su reserva.
- `CancellationService.cancelByCreator(Long instanceId, User creator)`: valida dueño; instancia `CANCELADO`; notificación **masiva** a todos los inscritos; cada `Payment → REFUNDED_FULL`; chat se archiva (flag `archived=true` en la instancia).
- `CancellationService.cancelByMinCapacity(Long instanceId)`: mismo efecto que Caso 2 (invocado por job **o** botón manual `POST /instances/{id}/cancel-min-capacity`, solo dueño).
- `NotificationService.notify(User to, NotificationType t, String message, String link)`; controller `GET /notifications` + `POST /notifications/{id}/read`.
- `ScheduledJobs` (`@Scheduled` + `@EnableScheduling` en la app): `completionJob()` cada minuto → instancias con `endAt <= now` y `SCHEDULED` → `COMPLETED` (idempotente — Review Focus #5) y luego `EscrowReleaseService.releaseIfDue`; `minCapacityJob()` cada minuto → instancias que iniciaron con asistentes `< minCapacity` → `cancelByMinCapacity` (todo dentro de try/catch + log — Review Focus de §8).

- [ ] **Step 1: Test unitario fallido** — `NotificationServiceTest`: `testNotifyPersisteNoLeida`, `testMasivaCreaUnaPorInscrito`.
- [ ] **Step 2: Ver fallar** → FAIL. **Step 3:** Implementar. **Step 4:** Ver pasar → PASS.
- [ ] **Step 5: Test unitario fallido** — `CancellationServiceTest`: `testCaso1LiberaCupoYNotificaAlCreador`, `testCaso1ReembolsoTotalMasDe24h`, `testCaso1ReembolsoParcialMenosDe24h` (50%), `testCaso2NotificaMasivaYRefundTotalYArchivaChat`, `testCaso3IgualQueCaso2`, `testNoPuedeCancelarReservaAjena` (NotOwnerException).
- [ ] **Step 6: Ver fallar** → FAIL. **Step 7:** Implementar `CancellationService`. **Step 8:** Ver pasar → PASS.
- [ ] **Step 9: Test unitario fallido** — `ScheduledJobsTest`: `testCompletionJobMarcaCompleted` (2ª corrida no-op — Review Focus #5), `testMinCapacityJobCancelaYReembolsa`, `testJobsNoPropaganExcepciones` (un repo que lanza → el job termina sin error).
- [ ] **Step 10: Ver fallar** → FAIL. **Step 11:** Implementar jobs + `@EnableScheduling`. **Step 12:** Ver pasar → PASS.
- [ ] **Step 13: Test MockMvc fallido** — `CancellationFlowTests`: participante cancela → redirect con flash de éxito y su reserva `CANCELADO`; creador cancela fecha → 200/302 y notificaciones visibles en `GET /notifications` para los inscritos.
- [ ] **Step 14: Ver fallar** → FAIL. **Step 15:** Implementar vistas (pantalla de notificaciones con los 3 tipos, botones de cancelación). **Step 16:** Ver pasar → PASS.
- [ ] **Step 17: Commit** — `feat: tres casos de cancelacion, notificaciones y jobs programados`.

### Task 7: Chat STOMP por instancia

**Files:**
- Create: `chat/entity/ChatMessage.java`, `chat/repository/ChatMessageRepository.java`, `chat/config/{WebSocketConfig, WebSocketAuthInterceptor}.java`, `chat/controller/ChatController.java`, `chat/service/ChatAuthorizationService.java`
- Create: `templates/chat/{list.html, room.html}`, `static/js/chat.js`
- Test: `chat/ChatAuthorizationServiceTest.java`, `chat/ChatStompTests.java`

**Interfaces:**
- `WebSocketConfig`: broker `/topic` (simple broker), endpoint `/ws` con SockJS, registro del interceptor de auth.
- `ChatAuthorizationService.canAccess(Long instanceId, User u) → boolean`: dueño **o** reservation `CONFIRMADA` en esa instancia; `sendMessage(Long instanceId, User u, String text)` lanza `AccessDeniedException` si no (Review Focus #3).
- Rutas: `GET /chats` (lista de grupos del usuario), `GET /chats/{instanceId}` (room + historial), `POST /chats/{instanceId}/send` (persiste y publica a `/topic/instances/{instanceId}`); `chat.js` se suscribe con STOMP sobre SockJS y rechaza conexión sin sesión.
- Instancia `archived=true` → room en solo-lectura (form oculto + aviso).

- [ ] **Step 1: Test unitario fallido** — `ChatAuthorizationServiceTest`: `testDuenoPuede`, `testInscritoConfirmadoPuede`, `testNoInscritoNoPuede` (Review Focus #3), `testArchivadoNoAdmiteMensajes`.
- [ ] **Step 2: Ver fallar** → FAIL. **Step 3:** Implementar service. **Step 4:** Ver pasar → PASS.
- [ ] **Step 5: Test STOMP fallido** — `ChatStompTests` (`@SpringBootTest` + cliente STOMP real): conecta a `ws://localhost:{port}/ws`, se suscribe a `/topic/instances/{id}`, publica vía `ChatController` y recibe el mensaje en <2s; instancia archivada → `sendMessage` lanza.
- [ ] **Step 6: Ver fallar** → FAIL. **Step 7:** Implementar config, controller, vistas, `chat.js`. **Step 8:** Ver pasar → PASS.
- [ ] **Step 9: Commit** — `feat: chat stomp por instancia con autorizacion`.

### Task 8: Configuración/perfil + pulido PWA + pruebas end-to-end

**Files:**
- Modify: `user/controller/ProfileController.java`, `templates/profile/settings.html`, `static/js/sw.js`, `static/manifest.webmanifest`
- Create: `src/test/java/com/openlab/mx/share/E2eSmokeTests.java`

**Interfaces:**
- `GET /settings` (nombre, avatar multipart→base64, vista SP "0 SP"), `POST /settings/update`, `POST /settings/logout`.
- `sw.js`: cachea shell (`/`, `css/app.css`, manifest e íconos) con cache-first; datos con network-first; `sw-register.js` registra en load.

- [ ] **Step 1: Test fallido** — `E2eSmokeTests` (MockMvc con sesión): login `demo_creator` → `/feed` 200 → crear experiencia → aparece en `/feed` → `demo_user` la ve, reserva, paga, el creador confirma, ambos ratean al terminar → escrow `RELEASED` con comisión 5%; y `GET /settings` muestra "SP".
- [ ] **Step 2: Ver fallar** → FAIL. **Step 3:** Implementar settings + pulido PWA (íconos, offline fallback). **Step 4:** Ver pasar → PASS.
- [ ] **Step 5: Verificar build completo** — `export JAVA_HOME="/c/Program Files/Java/jdk-25.0.2" && ./mvnw test` → toda la suite en verde.
- [ ] **Step 6: Commit** — `feat: configuracion de perfil, pulido pwa y smoke e2e`.

---

## Self-Review (del plan)

1. **Cobertura del spec:** §5.1→Task 2; §5.2→Task 4; §5.3→Task 5; §5.4→Task 6; §5.5→Task 6; §5.6→Task 7; §6 vistas→tasks 1–8; §7 seguridad→tasks 2,3,7; §8 errores→tasks 3,5,6 (`GlobalExceptionHandler` se crea en Task 3, primer task con errores de dominio, y lo reutilizan los demás); §9 pruebas→incluidas por task; §10 seed→Task 2; §11 jobs→Task 6; §12 orden→orden de los tasks.
2. **Tipos consistentes:** `EscrowService.tryRelease(Long)`, `ExperienceFeedDTO`, `ExperienceCategory`, `NotificationType` usados igual en todos los tasks.
3. **Review Focus:** los 5 puntos tienen su test en el task dueño (1→Task 5, 2→Task 5, 3→Tasks 3/7, 4→Task 4, 5→Tasks 5/6).
