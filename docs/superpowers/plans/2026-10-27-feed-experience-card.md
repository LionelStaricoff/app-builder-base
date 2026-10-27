# Card de experiencia en el feed (fechas, preguntas y reseñas) sin pantalla intermedia

> **Para quien implementa:** ejecutar tarea por tarea, con la suite verde al
> final de cada una. Marcar los checkboxes a medida que se completan.

**Goal:** que la card del feed sea la pantalla de decisión (fechas por fecha con
cupos y avatares, preguntas y reseñas expandibles y lazy) y que el pago vaya
directo a Stripe desde el detalle, eliminando `GET /checkout/{id}` y
`checkout.html`.

**Architecture:** la card no crea reservas ni sesiones. Pide fragmentos HTML a
`ExperienceCardController` (fechas, disponibilidad, preguntas, reseñas) y
deep-linkea al detalle con `instanceId` + `attendees`. El detalle preselecciona
esa fecha y su picker postea a `POST /reservations/{id}`, que ahora absorbe la
lógica de `CheckoutController.pay` (reserva PENDIENTE + Payment + Stripe
Session). El KYC sigue cortando antes: `UnverifiedIdentityException` →
`/verificacion`. Sin Stripe configurado, flash error y vuelta al detalle.

**Tech Stack:** Spring Boot MVC + JPA/Hibernate + Flyway, Thymeleaf, Spring
Security (CSRF), H2 en tests, Playwright para la verificación en navegador.

**Spec:** `docs/superpowers/specs/2026-10-27-feed-experience-card-design.md` — el
plan argumenta desde la spec; leerla antes de empezar.

**Fuera de alcance:** escrow, webhooks, payout, modelo de datos (sin migración),
rediseño de la card, unificación de las secciones del detalle con los fragmentos
lazy.

## Global Constraints

- Rama de trabajo: `feature/feed-experience-card`.
- Commits: Conventional Commits, scope con el casing real de la clase,
  descripción en imperativo castellano. Sin `Co-Authored-By` ni atribución de IA.
- Esquema: no hay migración. `ddl-auto=validate` sigue mandando.
- UI: clases y tokens existentes (`.feed-details`, `bg-gray-*`, `text-teal-*`,
  `rounded-*`). No introducir un design system nuevo.
- La card tiene `overflow-hidden`: el panel de fechas va en flujo normal, nunca
  absoluto.
- Scroll infinito: delegación de eventos en `document`; los listeners por card
  se rompen con las cards inyectadas.
- No build/package completo después de cada cambio; sí tests targeteados y e2e
  cuando la tarea lo pida.
- Baseline: suite Java verde antes de empezar. Cada tarea termina con su test
  targeteado verde.

## Review Focus

1. **Cambiar de fecha no pierde los cupos elegidos.** El input de cupos vive
   fuera del bloque `[data-card-capacity]`; solo se reemplaza la disponibilidad.
2. **La card no crea nada.** El CTA es un `GET` al detalle. Si alguien lo
   convierte en `POST`, se duplica la reserva y se rompe la idempotencia.
3. **Preguntas/reseñas no se piden dos veces.** `data-loaded` en el `<details>`
   después del primer fetch; el segundo toggle solo abre/cierra.
4. **Sesión vencida no se inyecta en la card.** `fetchFragment` corta con
   `res.redirected` o `content-type` que no sea `text/html`.
5. **El pago no pierde validaciones.** Cupos 1..20, KYC y sanitización siguen en
   `POST /reservations/{id}`; el checkout no era el que validaba.
6. **El `cancelUrl` de Stripe no apunta a una ruta muerta.** Metadata
   `experienceId` + `/experiences/{id}?instanceId=...#fechas`.
7. **Las cards del scroll infinito tienen el mismo comportamiento.** Sin
   `initFeedCards()` por card; todo por delegación.

---

### Task 1: DTO de fecha, consulta batcheada y servicio

**Files:**
- Create: `src/main/java/com/openlab/mx/share/experience/dto/ExperienceDateDTO.java`
- Create: `src/main/java/com/openlab/mx/share/experience/service/ExperienceCardService.java`
- Modify: `src/main/java/com/openlab/mx/share/experience/dto/ExperienceFeedDTO.java` (agregar `nextInstanceId`)
- Modify: `src/main/java/com/openlab/mx/share/experience/service/FeedService.java` (`toDto`, `nextInstanceId`)
- Modify: `src/main/java/com/openlab/mx/share/reservation/repository/ReservationRepository.java` (`findByInstanceIdInAndStatusNot`)

**Interfaces:**
- Consumes: `ExperienceInstanceRepository.findByExperienceIdAndDateGreaterThanEqualOrderByDateAsc`, `ReservationRepository.countAttendees`, `RefundPolicy.percentFor(ExperienceInstance)`.
- Produces:
  - `ExperienceDateDTO(Long instanceId, LocalDate date, LocalTime startTime, int maxCapacity, int availableCapacity, int reservedCount, List<String> reservedAvatars, Integer refundPercent)`
  - `ExperienceCardService.dates(Long experienceId) -> List<ExperienceDateDTO>`
  - `ExperienceCardService.availability(Long experienceId, Long instanceId) -> ExperienceDateDTO`
  - `ExperienceFeedDTO.nextInstanceId()`

- [x] **Step 1: Agregar `nextInstanceId` al DTO del feed**

En `ExperienceFeedDTO`, insertar `Long nextInstanceId` inmediatamente después de
`LocalDate nextDate`. En `FeedService.toDto`, resolverlo con `nextInstance(experience)`
y pasarlo en la posición correcta. No cambiar el orden de los demás campos.

- [x] **Step 2: Consulta batcheada de reservas**

En `ReservationRepository`:

```java
List<Reservation> findByInstanceIdInAndStatusNot(List<Long> instanceIds, ReservationStatus status);
```

- [x] **Step 3: `ExperienceDateDTO`**

Record con los campos de la spec §5.1. Javadoc corto: una fila por fecha
reservable, con lo que la card necesita para pintar cupos y avatares sin otra
llamada.

- [x] **Step 4: `ExperienceCardService`**

```java
@Service
@RequiredArgsConstructor
public class ExperienceCardService {

    private final ExperienceRepository experienceRepository;
    private final ExperienceInstanceRepository instanceRepository;
    private final ReservationRepository reservationRepository;
    private final RefundPolicy refundPolicy;

    @Transactional(readOnly = true)
    public List<ExperienceDateDTO> dates(Long experienceId) { ... }

    @Transactional(readOnly = true)
    public ExperienceDateDTO availability(Long experienceId, Long instanceId) { ... }
}
```

Reglas:
- Solo instancias `SCHEDULED` con `date >= hoy` (zona del sistema), ordenadas por
  fecha y hora.
- Una sola query de reservas: `findByInstanceIdInAndStatusNot(ids, CANCELADO)`.
- Agrupar por `instance.getId()`: `reservedCount` = suma de `attendees`;
  `reservedAvatars` = avatares no vacíos, distintos, limitados a `maxCapacity`.
- `availableCapacity = max(0, maxCapacity - reservedCount)`.
- `refundPercent = refundPolicy.percentFor(instance)`.
- `availability` valida que la instancia exista, sea de esa experiencia, esté
  `SCHEDULED` y sea futura; si no, `DomainException("Fecha no encontrada")`.
- Sin fechas: lista vacía, nunca `null`.

- [x] **Step 5: Test targeteado**

Crear `src/test/java/com/openlab/mx/share/experience/ExperienceCardServiceTests.java`:
- dos fechas futuras con 2 y 3 reservas de distintos `attendees` → cupos y
  avatares correctos por fecha;
- una instancia `CANCELLED` y una pasada no aparecen;
- `availability` de una instancia de otra experiencia lanza `DomainException`.

Correr: `./mvnw test -Dtest=ExperienceCardServiceTests`.

---

### Task 2: Endpoints de fragmentos (fechas, preguntas, reseñas)

**Files:**
- Create: `src/main/java/com/openlab/mx/share/experience/controller/ExperienceCardController.java`
- Create: `src/main/resources/templates/experience/card-dates.html`
- Create: `src/main/resources/templates/experience/card-questions.html`
- Create: `src/main/resources/templates/experience/card-reviews.html`

**Interfaces:**
- Consumes: `ExperienceCardService`, `QuestionService.questionsFor`, `QuestionService.ask`, `RatingService.reviewsFor`, `ExperienceService.get`, `CurrentUser`.
- Produces: los endpoints de la spec §5.2, todos `text/html`.

- [x] **Step 1: `card-dates.html` con dos fragmentos**

`picker(experienceId, dates, selectedInstanceId)`:
- Si `dates` está vacío: `<p>No hay fechas disponibles</p>`.
- Si no: un botón por fecha con `data-date-option`, `data-instance-id`,
  `data-date-label` (texto corto ya formateado con `UiLabels.fechaCorta` +
  hora), `th:disabled="${d.availableCapacity == 0}"`, y el texto
  `"<fecha> · <N> libres"`. El seleccionado lleva `ring-2 ring-teal-300`.
- Un `aria-label` por botón: "Elegir fecha <fecha>, <N> lugares libres".

`availabilityContent(maxCapacity, availableCapacity, reservedCount, reservedAvatars)`:
- El contenido interno del bloque de cupos: header "Lugares libres" + "N cupos",
  barra `reservedCount/maxCapacity`, avatares y "N reservaron" / "N libres".
- Mismo markup y clases que hoy en `fragments/feed-card.html:164-181`.

- [x] **Step 2: `card-questions.html`**

`section(experienceId, questions, puedePreguntar, esDueno, creatorName, ok, error)`:
- `data-count="${#lists.size(questions)}"` en el root.
- Lista de preguntas con avatar/nombre/fecha, cuerpo, respuesta o "Sin respuesta
  aún", y el form de responder solo si `esDueno and !q.answered`.
- Form de preguntar solo si `puedePreguntar`, con
  `th:action="@{/experiences/{id}/questions(id=${experienceId})}"`,
  `data-question-form`, `data-fragment-action="@{/experiences/{id}/questions/fragment(id=${experienceId})}"`.
- Mensajes `ok`/`error` visibles arriba.
- Si no hay preguntas: empty state con el texto del detalle.
- Sin login: link a `/login` (mismo texto que el detalle).

- [x] **Step 3: `card-reviews.html`**

`section(reviews)`:
- `data-count="${#lists.size(reviews)}"`.
- Empty state: "Todavía no hay reseñas."
- Cada reseña: username, mes/año, estrellas, texto si existe. Mismo markup que
  `experience/detail.html:319-335`.

- [x] **Step 4: `ExperienceCardController`**

```java
@Controller
@RequiredArgsConstructor
public class ExperienceCardController {

    private final ExperienceCardService cardService;
    private final ExperienceService experienceService;
    private final QuestionService questionService;
    private final RatingService ratingService;
    private final CurrentUser currentUser;

    @GetMapping("/experiences/{id}/dates")
    public String dates(@PathVariable Long id,
                        @RequestParam(required = false) Long instanceId,
                        Model model) { ... }

    @GetMapping("/experiences/{id}/dates/{instanceId}")
    public String availability(@PathVariable Long id,
                               @PathVariable Long instanceId,
                               Model model) { ... }

    @GetMapping("/experiences/{id}/questions")
    public String questions(@PathVariable Long id, Model model) { ... }

    @PostMapping("/experiences/{id}/questions/fragment")
    public String askFragment(@PathVariable Long id,
                              @Valid @ModelAttribute("questionRequest") QuestionRequest request,
                              BindingResult bindingResult,
                              Model model) { ... }

    @GetMapping("/experiences/{id}/reviews")
    public String reviews(@PathVariable Long id, Model model) { ... }
}
```

Reglas:
- `questions` y `askFragment` comparten un helper `cargarPreguntas(id, model, ok, error)`.
- `esDueno` = usuario actual != null y `experience.creator.id == user.id`.
- `puedePreguntar` = usuario != null y `!esDueno`.
- `askFragment`: si `bindingResult.hasErrors()`, setear `error` ("Escribí una
  pregunta de entre 3 y 500 caracteres") y devolver el fragmento. Si
  `questionService.ask` lanza `DomainException`, setear `error` con el mensaje.
  Si sale bien, `ok` ("Pregunta enviada: la respuesta queda publicada acá").
- Todos devuelven `"experience/card-... :: ..."` (la sintaxis `template :: selector`
  que ya usa `FeedController.feedCards`).

- [x] **Step 5: Tests MockMvc**

Crear `src/test/java/com/openlab/mx/share/experience/ExperienceCardFragmentsTests.java`:
- `GET /experiences/{id}/dates` devuelve 200, `text/html`, las dos fechas y
  `data-instance-id`.
- `GET /experiences/{id}/dates/{instanceId}` devuelve la disponibilidad de esa
  fecha (cupos y avatares correctos).
- `GET /experiences/{id}/questions` con un usuario distinto al dueño muestra el
  form de preguntar; con el dueño muestra el de responder y no el de preguntar.
- `POST /experiences/{id}/questions/fragment` con body válido devuelve 200 y el
  texto de la pregunta; con body de 2 caracteres devuelve 200 y el error.
- `GET /experiences/{id}/reviews` devuelve la reseña sembrada.
- `GET /experiences/{id}/dates` sin sesión redirige a `/login`.

Correr: `./mvnw test -Dtest=ExperienceCardFragmentsTests`.

---

### Task 3: Card del feed con fechas, cupos por fecha, preguntas y reseñas

**Files:**
- Modify: `src/main/resources/templates/fragments/feed-card.html`
- Modify: `src/main/resources/templates/feed.html` (incluir `feed-card.js`)
- Modify: `src/main/resources/templates/favorites/favorites.html` (incluir `feed-card.js`)

**Interfaces:**
- Consumes: `dto.nextInstanceId`, `dto.maxCapacity`, `dto.availableCapacity`,
  `dto.reservedCount`, `dto.reservedAvatars`, y los fragmentos de la Task 2.
- Produces: atributos `data-dates-toggle`, `data-dates-panel`,
  `data-date-option`, `data-card-capacity`, `data-card-date-label`,
  `data-card-instance-id`, `data-lazy-section`, `data-lazy-url`,
  `data-lazy-content`, `data-lazy-count`, `data-question-form`.

- [x] **Step 1: Fecha como botón**

En la celda de fecha del grid de 3 (`feed-card.html:91-95`), reemplazar el
`<span>` por:

```html
<button type="button"
        data-dates-toggle
        th:disabled="${dto.nextInstanceId == null}"
        aria-expanded="false"
        class="flex flex-col items-center gap-1.5 rounded-xl px-2 py-1 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-teal-200 disabled:opacity-50">
  <svg .../>
  <span class="font-bold text-gray-900" data-card-date-label
        th:text="${dto.nextDate != null ? T(...UiLabels).fechaCorta(dto.nextDate) : 'Sin fecha'}">24 oct</span>
  <span class="text-[9px] font-semibold text-teal-700"
        th:text="${dto.nextInstanceId != null ? 'Cambiar' : 'Sin fechas'}">Cambiar</span>
</button>
```

- [x] **Step 2: Panel de fechas en flujo normal**

Después del grid de 3 columnas (antes de "Ver Recorrido"), agregar:

```html
<div data-dates-panel hidden class="rounded-2xl border border-gray-100 bg-white p-3"></div>
```

No usar `absolute`: la card es `overflow-hidden` y el panel se recortaría.

- [x] **Step 3: Bloque de cupos reutilizable**

Reemplazar el contenido del `col-span-3` (`feed-card.html:164-181`) por:

```html
<div class="col-span-3 bg-gray-50 p-4 rounded-2xl border border-gray-100 flex flex-col justify-between space-y-3"
     data-card-capacity th:attr="data-instance-id=${dto.nextInstanceId}">
  <div th:replace="~{experience/card-dates :: availabilityContent(
        ${dto.maxCapacity}, ${dto.availableCapacity}, ${dto.reservedCount}, ${dto.reservedAvatars})}"></div>
</div>
```

El bloque de precio (`col-span-2`) queda igual.

- [x] **Step 4: Preguntas y reseñas lazy**

Después del grid de cupos/precio y antes del CTA, agregar dos `<details>`:

```html
<details class="feed-details border-b border-gray-100 pb-2"
         data-lazy-section
         th:attr="data-lazy-url=@{/experiences/{id}/questions(id=${dto.id})}">
  <summary class="...">
    <span class="flex items-center gap-2">
      <svg .../>
      Preguntas <span data-lazy-count class="text-gray-400"></span>
    </span>
    <svg class="feed-details-chevron ..." .../>
  </summary>
  <div data-lazy-content class="py-2">
    <p class="text-[11px] text-gray-400">Tocá para cargar las preguntas.</p>
  </div>
</details>
```

Idéntico para reseñas con `data-lazy-url=@{/experiences/{id}/reviews(id=${dto.id})}`
y el texto "Tocá para cargar las reseñas.".

- [x] **Step 5: CTA con fecha y cupos**

Reemplazar el `<a>` de "Reservar ahora" (`feed-card.html:191-196`) por:

```html
<form method="get" th:action="@{/experiences/{id}(id=${dto.id})}"
      data-card-booking class="space-y-2">
  <input type="hidden" name="instanceId" data-card-instance-id th:value="${dto.nextInstanceId}">
  <div class="flex items-center gap-2">
    <label class="flex items-center gap-1.5 text-[11px] font-semibold text-gray-500">
      Cupos
      <input type="number" name="attendees" min="1" max="20" value="1"
             class="w-16 rounded-lg border border-gray-200 px-2 py-1.5 text-xs text-gray-900 focus:outline-none focus:ring-2 focus:ring-rose-200">
    </label>
    <button type="submit"
            th:disabled="${dto.nextInstanceId == null}"
            class="flex-1 bg-gray-100 hover:bg-gray-200 active:scale-[0.99] text-gray-900 font-bold py-3.5 px-5 rounded-2xl flex items-center justify-between border border-gray-200 transition-all shadow-sm disabled:opacity-50">
      <span class="mx-auto text-sm">Reservar ahora</span>
      <svg .../>
    </button>
  </div>
</form>
```

- [x] **Step 6: Incluir el JS**

En `feed.html`, antes de `</body>`, agregar
`<script th:src="@{/js/feed-card.js}"></script>` después de `feed-infinite.js`.
En `favorites.html`, después de `carousel.js`.

- [x] **Step 7: Test de template**

Extender `src/test/java/com/openlab/mx/share/FeedAndFavoritesControllerTests.java`
(o crear `FeedCardMarkupTests`):
- el HTML del feed contiene `data-dates-toggle`, `data-lazy-url="/experiences/<id>/questions"`,
  `data-lazy-url="/experiences/<id>/reviews"`, `data-card-booking` y el
  `instanceId` de la próxima fecha;
- el input de cupos está fuera de `[data-card-capacity]` (buscar el orden en el
  HTML o al menos que ambos existen).

Correr: `./mvnw test -Dtest=FeedAndFavoritesControllerTests`.

---

### Task 4: JS de la card (delegación, fetch de fragmentos, sin recargas)

**Files:**
- Create: `src/main/resources/static/js/feed-card.js`

**Interfaces:**
- Consumes: los `data-*` de la Task 3 y los endpoints de la Task 2.
- Produces: `window.__feedCard` no es necesario; todo vive en el IIFE. Expone
  `window.feedCardFetchFragment` solo si un test lo necesita (evitar).

- [x] **Step 1: Helpers**

```js
async function fetchFragment(url, options = {}) {
  const res = await fetch(url, {
    credentials: 'same-origin',
    headers: { Accept: 'text/html', ...(options.headers || {}) },
    ...options,
  });
  const type = res.headers.get('content-type') || '';
  if (!res.ok || res.redirected || !type.includes('text/html')) {
    throw new Error('No se pudo cargar la sección');
  }
  return (await res.text()).trim();
}
```

- [x] **Step 2: Delegación de fechas**

Un solo `document.addEventListener('click', ...)`:
- `[data-dates-toggle]`: buscar el panel hermano `[data-dates-panel]`. Si está
  vacío, `fetchFragment('/experiences/' + id + '/dates')`, inyectar, marcar
  `data-loaded`. Toggle `hidden` y `aria-expanded`. En error, mostrar
  "No se pudieron cargar las fechas" dentro del panel.
- `[data-date-option]`: leer `data-instance-id`, `data-date-label`. Pedir
  `/experiences/<id>/dates/<instanceId>`, reemplazar el `innerHTML` del
  `[data-card-capacity]`, actualizar `data-instance-id` del bloque, el
  `[data-card-date-label]`, el `[data-card-instance-id]` del form, marcar el
  botón seleccionado, cerrar el panel. En error, dejar la fecha anterior y
  mostrar un mensaje en el panel.

- [x] **Step 3: Delegación de lazy sections**

`document.addEventListener('toggle', ...)` no burbujea. Usar captura:
`document.addEventListener('toggle', handler, true)` y filtrar
`event.target.matches('details[data-lazy-section]')`. Si `open` y no
`data-loaded`: `fetchFragment(dataset.lazyUrl)`, inyectar en
`[data-lazy-content]`, `data-loaded = '1'`, y actualizar `[data-lazy-count]` con
el `data-count` del root del fragmento. Si falla, mostrar el error y dejar
`data-loaded` sin setear para permitir reintento.

- [x] **Step 4: Form de preguntar**

Delegación de `submit` en `document` para `form[data-question-form]`:
- `preventDefault()`.
- `fetch(form.dataset.fragmentAction, { method: 'POST', body: new FormData(form), headers: { Accept: 'text/html' } })`.
- Reemplazar el `[data-lazy-content]` del `<details>` contenedor con la respuesta.
- Actualizar el count.
- En error de red, mostrar "No se pudo enviar la pregunta".
- Sin JS, el form sigue posteando al endpoint normal (fallback).

- [x] **Step 5: Verificación manual mínima**

Con la app levantada, en `/feed`: abrir fechas, cambiar de fecha, ver que el
bloque de cupos se reemplaza y el label cambia; abrir/cerrar preguntas y
reseñas; preguntar. No se considera terminado sin esto.

---

### Task 5: Detalle con fecha y cupos preseleccionados

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/experience/controller/ExperienceController.java` (`detail`)
- Modify: `src/main/resources/templates/experience/picker-modal.html`
- Modify: `src/main/resources/templates/experience/detail.html`

**Interfaces:**
- Consumes: `?instanceId=&attendees=` del CTA de la card.
- Produces: `selectedInstanceId`, `selectedAttendees` en el modelo; el picker de
  esa fecha con el valor elegido.

- [x] **Step 1: Params en `detail`**

```java
@GetMapping("/experiences/{id}")
public String detail(@PathVariable Long id,
                     @RequestParam(required = false) Long instanceId,
                     @RequestParam(defaultValue = "1") int attendees,
                     Model model) {
    ...
    model.addAttribute("selectedInstanceId", instanceId);
    model.addAttribute("selectedAttendees", Math.max(1, Math.min(attendees, 20)));
    ...
}
```

- [x] **Step 2: Picker con cupos**

`picker-modal.html` pasa a `picker(instanceId, refundPercent, attendees)` y el
input usa `th:value="${attendees != null ? attendees : 1}"`. Actualizar el único
llamador (`detail.html:351`).

- [x] **Step 3: Preselección y foco**

En `detail.html`:
- El bloque de cada fecha lleva
  `th:classappend="${inst.id == selectedInstanceId} ? 'ring-2 ring-rose-300' : ''"`.
- El picker recibe `${inst.id == selectedInstanceId ? selectedAttendees : 1}`.
- La celda "Próxima fecha" del grid superior pasa a un `<a href="#fechas">` con
  el mismo texto, para que sea clickeable.

- [x] **Step 4: Test**

Extender `src/test/java/com/openlab/mx/share/ReservationFlowTests.java`:
- `GET /experiences/{id}?instanceId=<id>&attendees=3` devuelve el HTML con
  `value="3"` en el input de cupos de esa fecha.

Correr: `./mvnw test -Dtest=ReservationFlowTests`.

---

### Task 6: Eliminar la pantalla intermedia y pagar directo

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/reservation/controller/ReservationController.java`
- Modify: `src/main/java/com/openlab/mx/share/payment/service/StripeCheckoutService.java` (metadata `experienceId`)
- Modify: `src/main/java/com/openlab/mx/share/payment/gateway/StripePaymentGateway.java` (`cancelUrl`)
- Delete: `src/main/java/com/openlab/mx/share/payment/controller/CheckoutController.java`
- Delete: `src/main/java/com/openlab/mx/share/payment/dto/CheckoutPayRequest.java`
- Delete: `src/main/resources/templates/checkout.html`
- Delete: `src/test/java/com/openlab/mx/share/CheckoutReviewTests.java`
- Modify: `src/test/java/com/openlab/mx/share/RefundPolicyViewTests.java` (borrar el test del checkout)

**Interfaces:**
- Consumes: `StripeCheckoutService.start(Long instanceId, int attendees, User user) -> GatewaySession`, `ExperienceInstanceRepository.findById`.
- Produces: `POST /reservations/{id}` → 302 a `session.url()`; `GET /checkout/{id}` → 404.

- [x] **Step 1: `ReservationController.reserve` inicia el pago**

Inyectar `StripeCheckoutService` y `ExperienceInstanceRepository`. Reemplazar el
redirect al checkout por:

```java
int cupos = sanitize(request != null ? request.attendees() : 1);
try {
    GatewaySession session = stripeCheckoutService.start(instanceId, cupos, user);
    return "redirect:" + session.url();
} catch (RuntimeException e) {
    redirectAttributes.addFlashAttribute("error", e.getMessage());
    Long experienceId = instanceRepository.findById(instanceId)
            .map(i -> i.getExperience().getId())
            .orElse(null);
    String base = experienceId != null ? "/experiences/" + experienceId : "/feed";
    return "redirect:" + base + "?instanceId=" + instanceId
            + "&attendees=" + cupos + "#fechas";
}
```

Mantener `requireVerifiedIdentityService.requireVerified(user.getId())` antes de
`start` (el `UnverifiedIdentityException` sigue yendo a `/verificacion`).

- [x] **Step 2: Metadata y cancelUrl**

En `StripeCheckoutService.start`, agregar al `Map.of`:
`"experienceId", String.valueOf(instance.getExperience().getId())`.

En `StripePaymentGateway.createCheckout`, cambiar:

```java
.setCancelUrl(baseUrl + "/experiences/" + q.metadata().get("experienceId")
        + "?instanceId=" + q.metadata().get("instanceId")
        + "&attendees=" + q.metadata().get("attendees") + "#fechas")
```

- [x] **Step 3: Borrar el checkout**

Eliminar los archivos listados. Verificar que no queden imports ni referencias:

```bash
grep -rn "CheckoutController\|CheckoutPayRequest\|checkout.html\|/checkout/" src
```

Solo deben quedar los tests que todavía se van a actualizar en la Task 7.

- [x] **Step 4: Tests targeteados de la eliminación**

- `GET /checkout/{id}` → 404.
- `POST /reservations/{id}` con cupos 2 → 302 a `/__fake_stripe_/checkout/...`,
  reserva `PENDIENTE`, payment `PENDING`.
- Doble submit → misma URL de sesión, sin duplicar reserva/payment.
- Sin KYC → 302 a `/verificacion`.
- Sin Stripe keys → 302 al detalle con flash `error` y nada persistido
  (reescribir `UnconfiguredCheckoutTests` a `POST /reservations/{id}`).

Correr: `./mvnw test -Dtest=UnconfiguredCheckoutTests,PaymentSuccessFlowTests,ReservationFlowTests,IdentityGateFlowTests`.

---

### Task 7: Actualizar la suite Java existente

**Files:**
- Modify: `src/test/java/com/openlab/mx/share/ReservationFlowTests.java`
- Modify: `src/test/java/com/openlab/mx/share/E2eSmokeTests.java`
- Modify: `src/test/java/com/openlab/mx/share/IdentityGateFlowTests.java`
- Modify: `src/test/java/com/openlab/mx/share/PaymentSuccessFlowTests.java`
- Modify: `src/test/java/com/openlab/mx/share/CancellationFlowTests.java`
- Modify: `src/test/java/com/openlab/mx/share/UnconfiguredCheckoutTests.java`
- Modify: `src/test/java/com/openlab/mx/share/RefundPolicyViewTests.java`

**Interfaces:** ninguna nueva; es actualización mecánica de URLs y expectativas.

- [x] **Step 1: Cambiar `/checkout/{id}` por `/reservations/{id}`**

En todos los POST de esos archivos. Las expectativas de redirect pasan de
`/checkout/{id}?attendees=N` a `/__fake_stripe_/checkout/...` (cuando el fake
está activo) o a `/verificacion` (sin KYC).

- [x] **Step 2: Quitar los GET al checkout**

`ReservationFlowTests.flujoReservaCheckoutYConfirmation` ya no navega al
checkout: el primer POST devuelve la URL de Stripe. Ajustar el nombre y los
pasos. `RefundPolicyViewTests`: borrar `elCheckoutMuestraElCartelYElPorcentajeDeEstaFecha`
(el detalle ya tiene su test).

- [x] **Step 3: Suite completa**

Correr `./mvnw test`. Debe quedar verde. Si algún test depende de la pantalla de
revisión, se elimina o se reescribe contra el detalle; no se agrega una pantalla
nueva para que pase.

---

### Task 8: E2E Playwright + documentación

**Files:**
- Modify: `e2e/tests/helpers.ts` (fechas extra y `createExperienceViaApi` devuelve el id)
- Create: `e2e/tests/feed-card.spec.ts`
- Modify: `docs/superpowers/specs/2026-10-27-feed-experience-card-design.md` (estado final)

**Interfaces:**
- Consumes: la app levantada por `playwright.config.ts` (H2 + KYC off).
- Produces: cobertura del punto ciego de MockMvc: el navegador armando los
  fetches y el form del CTA.

- [x] **Step 1: Sembrar dos fechas**

En `helpers.ts`, agregar `extraDate?: string` a `SEED` y, si existe, mandar
`schedules[1].date/startTime/endTime`. `createExperienceViaApi` devuelve el
`id` parseado del `Location` (para el test de preselección). `global-setup`
ignora el retorno.

- [x] **Step 2: `feed-card.spec.ts`**

Casos:
1. Abrir la card de `E2E Cerca`, tocar la fecha, ver el panel con las dos
   fechas. Elegir la segunda: el label cambia y el `[data-card-capacity]`
   cambia su `data-instance-id`.
2. Tocar "Preguntas": se carga el empty state o la lista. Cerrar y abrir de
   nuevo: no hay un segundo request (contar con `page.on('request')`).
3. Escribir una pregunta y enviarla: aparece en la card sin cambiar de URL.
4. Tocar "Reseñas": se carga el empty state; cerrar y abrir no vuelve a pedir.
5. "Reservar ahora": la URL es
   `/experiences/<id>?instanceId=<id>&attendees=1`, y el input de cupos de esa
   fecha en el detalle tiene `value="1"`.

- [x] **Step 3: Correr e2e**

`cd e2e; npx playwright test`. Verde. Si falla el preflight, agregar clases a
los controles nuevos (el input de cupos y el textarea ya las tienen).

- [x] **Step 4: Grafo y docs**

`graft build` para refrescar el grafo. Actualizar el estado del spec a
"implementado". No crear README ni docs extra.

---

## Verificación final

- [x] `./mvnw test` verde: 433 tests, 0 fallas, 0 errores (78 clases).
- [x] `cd e2e; npx playwright test` verde: 17 tests.
- [x] Navegador real (Playwright): `/feed` → cambiar fecha → cupos/avatares
      cambian sin recarga → preguntas y reseñas expanden → "Reservar ahora" →
      detalle con fecha/cupos → "Reservar" → Stripe (o `/verificacion`).
- [x] `grep -rn "/checkout/" src/main` solo devuelve el path del doble de
      pruebas (`FakePaymentGateway`: `/__fake_stripe_/checkout/`), que simula la
      página hosted de Stripe; ninguna ruta propia.
- [x] `checkout.html` no existe.
