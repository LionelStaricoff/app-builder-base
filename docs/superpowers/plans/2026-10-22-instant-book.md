# Instant Book (confirmar la reserva al pagar) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el pago confirme la reserva (`PENDIENTE → CONFIRMADA` en `StripeCheckoutService.complete()`) para que el huésped entre al chat grupal al instante, con la confirmación manual del creador eliminada.

**Architecture:** Un solo punto de confirmación en `complete()` (lo llaman `/pago/exito` y el webhook), en la misma transacción del `PENDING → HELD`, seguido de notificaciones `RESERVA_CONFIRMADA` a huésped y creador. Escrow, cupos y cancelaciones quedan intactos. Migración `V3` de backfill para reservas históricas ya pagadas.

**Tech Stack:** Spring Boot (MVC + JPA/Hibernate + Flyway), Thymeleaf, Spring Security Test (MockMvc), H2 en tests, Stripe vía `PaymentGateway` con `TestGatewayConfig` (fake).

**Spec:** `docs/superpowers/specs/2026-10-22-instant-book-design.md` — el plan argumenta desde la spec; el executor debe leerla.

**Fuera de este plan** (spec propio aparte, decidido con el usuario): subcategorías de experiencias (tabla + seeder + campo) — no mezclar.

## Global Constraints

- Rama de trabajo: `feature/v2`; un commit por tarea terminada.
- Commits: Conventional Commits, scope con el casing real de la clase, descripción en **imperativo castellano**, footer `🤖 Generated with Codebuff` + `Co-Authored-By: Codebuff <noreply@codebuff.com>`.
- Esquema **solo vía Flyway** (`ddl-auto=validate`); SQL portable **Postgres + H2** (sin `ON CONFLICT`, sin alias en `UPDATE`).
- Enums persistidos como texto: `@Enumerated(EnumType.STRING)` → comparar `'PENDIENTE'`, `'CONFIRMADA'`, `'HELD'`.
- **Escrow intocado**: `EscrowService.tryRelease` sigue exigiendo `HELD` + instancia `COMPLETED` + (todos evaluaron o ventana de 2 días).
- UI: solo clases/variables de marca existentes (`.hint`, `.btn-*`, `--color-*`); nada del look del prototipo.
- Baseline: suite completa **301 tests en verde** antes de empezar; cada tarea termina con la suite verde.
- Los enums/estados que quedan: `PENDIENTE` = solo pre-pago; `CONFIRMADA` = pago completado; `CANCELADO` sin cambios.

## Review Focus

Condiciones que la spec implícitamente exige y que los tests de tarea deben fijar:

1. **Retorno y webhook llegan para la misma sesión** → la segunda llamada a `complete()` no re-confirma ni duplica avisos. Fijado en Task 1 (`completeIdempotenteNoDuplicaConfirmacionNiAvisos`).
2. **Checkout abandonado** (`checkout.session.expired`) → la reserva **nunca** queda `CONFIRMADA`: termina `CANCELADO`. Fijado en Task 1 (extensión de `expiredFallaElPagoYLiberaCupo`).
3. **Liberación temprana del escrow**: confirmar al pagar no libera dinero — el pago debe seguir `HELD` tras `/pago/exito`. Fijado en Task 1 (`elPagoConfirmaLaReservaYAbreElChat`).
4. **Datos históricos**: reservas `PENDIENTE` con pago `HELD` ya existentes → backfill las confirma; `PENDIENTE` con pago `PENDING` → intacta. Fijado en Task 4 (`backfillConfirmaSoloReservasPagadas`).
5. **Reserva sin pagar en `/confirmation`** → el aviso habla del pago (no de una confirmación manual inexistente) y no hay link al chat. Fijado en Task 3 (`pendienteMuestraAvisoSinLinkDeChat`).

---

### Task 1: Confirmar al pagar y notificar a los dos

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/payment/service/StripeCheckoutService.java` (`complete()`, líneas ~100-130)
- Modify: `src/main/java/com/openlab/mx/share/payment/controller/CheckoutController.java` (bloque `SOLICITUD_RESERVA` en `pay()`)
- Modify: `src/main/java/com/openlab/mx/share/reservation/repository/ReservationRepository.java` (finder nuevo para el helper del test)
- Test: `src/test/java/com/openlab/mx/share/PaymentSuccessFlowTests.java` (3 tests nuevos)
- Modify: `src/test/java/com/openlab/mx/share/CancellationFlowTests.java` (`reservarYConfirmarGeneranNotificaciones`, líneas ~178-200)
- Modify: `src/test/java/com/openlab/mx/share/payment/controller/StripeWebhookControllerTests.java` (`expiredFallaElPagoYLiberaCupo`, línea ~171)

**Interfaces:**
- Consumes: `StripeCheckoutService.complete(String sessionId, Long userId)` (ya inyecta `ReservationRepository` y `Payment` lleva `getReservation()`), `NotificationService.notify(User, NotificationType, String, String)`.
- Produces (para Tasks 2-4): tras `complete()` la reserva queda `ReservationStatus.CONFIRMADA`; existen avisos `RESERVA_CONFIRMADA` con link `/chats/{instanceId}` (huésped) y `/reservations` (creador); `ReservationRepository.findFirstByInstanceIdAndUserId(Long, Long) → Optional<Reservation>` (nuevo).

- [ ] **Step 1: Arreglar el helper de lookup (infraestructura de test)**

En `ReservationRepository` agregar:

```java
java.util.Optional<Reservation> findFirstByInstanceIdAndUserId(Long instanceId, Long userId);
```

En `PaymentSuccessFlowTests`: el helper `pago(instanceId, username)` (línea 78) **deja de filtrar por `PENDIENTE`** — pasa a usar `findFirstByInstanceIdAndUserId`. Sin esto, cualquier lookup post-pago revienta con `NoSuchElementException`. Agregar campos `@Autowired NotificationRepository notificationRepository` y `@Autowired StripeCheckoutService stripeCheckoutService`, más el import de `ReservationStatus`/`PaymentStatus`/`NotificationType` que falten. Este cambio es infraestructura de test, sin lógica nueva: correr `./mvnw test -Dtest=PaymentSuccessFlowTests` y verificar que los tests existentes de la clase siguen **verdes**.

- [ ] **Step 2: Escribir los 3 tests rojos**

Los 3 tests nuevos, con el mismo patrón de la clase (`@SpringBootTest` + `@WithMockUser("demo_user")` + `fechaFutura()` + `TestGatewayConfig` ya importado):

```java
@Test
@WithMockUser("demo_user")
void elPagoConfirmaLaReservaYAbreElChat() throws Exception {
    Long instanceId = fechaFutura();
    mockMvc.perform(post("/checkout/" + instanceId).with(csrf()).param("attendees", "2"))
            .andExpect(status().is3xxRedirection());
    String sessionId = pago(instanceId, "demo_user").getStripeSessionId();
    mockMvc.perform(get("/pago/exito").param("session_id", sessionId))
            .andExpect(status().is3xxRedirection());

    Long userId = userRepository.findByUsername("demo_user").orElseThrow().getId();
    Reservation r = reservationRepository.findFirstByInstanceIdAndUserId(instanceId, userId).orElseThrow();
    assertEquals(ReservationStatus.CONFIRMADA, r.getStatus(), "el pago confirma la reserva");
    assertEquals(PaymentStatus.HELD, pago(instanceId, "demo_user").getStatus(),
            "el escrow no se libera al confirmar");                       // Review Focus 3
    mockMvc.perform(get("/chats/" + instanceId)).andExpect(status().isOk());
}

@Test
@WithMockUser("demo_user")
void elPagoNotificaAlHuespedYAlCreador() throws Exception {
    notificationRepository.deleteAll();
    Long instanceId = fechaFutura();
    // mismo flujo: POST /checkout → /pago/exito (extraer un privado pagar(instanceId) si se repite)
    Long huespedId = userRepository.findByUsername("demo_user").orElseThrow().getId();
    Long creadorId = userRepository.findByUsername("demo_creator").orElseThrow().getId();

    assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(huespedId).stream().anyMatch(
            n -> n.getType() == NotificationType.RESERVA_CONFIRMADA
                 && ("/chats/" + instanceId).equals(n.getLink())), "aviso al huésped con link al chat");
    assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(creadorId).stream().anyMatch(
            n -> n.getType() == NotificationType.RESERVA_CONFIRMADA
                 && "/reservations".equals(n.getLink())), "aviso al creador");
    assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(creadorId).stream().noneMatch(
            n -> n.getType() == NotificationType.SOLICITUD_RESERVA), "el aviso previo al cobro ya no existe");
}

@Test
@WithMockUser("demo_user")
void completeIdempotenteNoDuplicaConfirmacionNiAvisos() throws Exception {
    notificationRepository.deleteAll();
    Long instanceId = fechaFutura();
    // flujo hasta /pago/exito; sessionId y userId como arriba
    Long huespedId = ...; Long creadorId = ...;
    long avisos = contarConfirmadas(huespedId) + contarConfirmadas(creadorId); // helper local

    stripeCheckoutService.complete(sessionId, userId);   // 2ª llamada (retorno/webhook repetido)

    assertEquals(avisos, contarConfirmadas(huespedId) + contarConfirmadas(creadorId),
            "la segunda llamada no duplica RESERVA_CONFIRMADA");
}
```

- [ ] **Step 3: Correr y verificar el rojo**

Run: `./mvnw test -Dtest=PaymentSuccessFlowTests`
Expected: FAIL — `elPagoConfirmaLaReservaYAbreElChat` (`expected CONFIRMADA but was PENDIENTE`); los de notificaciones fallan por ausencia de avisos.

- [ ] **Step 4: Implementar la confirmación y los avisos en `complete()`**

Inyectar `private final NotificationService notificationService;` en `StripeCheckoutService`. En la rama que hace `PENDING → HELD` (donde hoy se hacen `setStatus/setHeldAt` y `paymentRepository.save(pago)`), persistir el pago primero y luego llamar a un método privado nuevo:

```java
private void confirmarAlPagar(Reservation reserva)
```

Comportamiento (spec §6): si `reserva == null` o `reserva.getStatus() != ReservationStatus.PENDIENTE` → retornar (ya CONFIRMADA o cancelada: nada que hacer). Si no: `setStatus(CONFIRMADA)`, `reservationRepository.save(reserva)`, y dos `notify(...)` de tipo `RESERVA_CONFIRMADA`:
- huésped: mensaje `Tu reserva en {nombre} está confirmada — entrá al chat grupal`, link `"/chats/" + reserva.getInstance().getId()`;
- creador (si `experience.getCreator() != null`): mensaje `"{username} reservó {attendees} lugar(es) en {nombre}"`, link `"/reservations"`.

Todo dentro del método `@Transactional complete(...)` existente. `complete()` ya sale temprano si el pago no es `PENDING` → la rama solo se alcanza una vez (idempotencia sin código extra). Mantener el orden actual: la validación de dueño (`NotOwnerException`) **antes** de mutar nada.

**Rompe la compilación a propósito:** `StripeCheckoutServiceTest` construye el servicio posicionalmente. Colocar el campo `private final NotificationService notificationService;` **después de `paymentRepository`** (antes de `gateway`) y actualizar el `setUp()` del test a `new StripeCheckoutService(reservationService, reservationRepository, paymentRepository, notificationService, gateway)` con `@Mock NotificationService notificationService;` nuevo. Las fixtures de reserva ya traen `instance` con nombre y sin creador → el aviso al creador se salta (null-safe) y el mock absorbe el aviso al huésped.

- [ ] **Step 5: Eliminar el aviso previo al cobro**

En `CheckoutController.pay()` quitar el bloque `if (nuevaReserva) { ... notify(creador, SOLICITUD_RESERVA, ...) }` y, si `notificationService` queda sin uso en la clase, el campo y su import.

- [ ] **Step 6: Correr los tests nuevos en verde**

Run: `./mvnw test -Dtest=PaymentSuccessFlowTests`
Expected: PASS (3 nuevos + los existentes de la clase).

- [ ] **Step 7: Reconvertir el test de notificaciones de `CancellationFlowTests`**

`reservarYConfirmarGeneranNotificaciones` (línea 178) asumía: creador avisado al crear la sesión y `POST /confirm` manual. Renombrar a `reservarYPagarGeneranNotificaciones` y llevarlo al flujo nuevo: `POST /checkout` → recuperar el `sessionId` con `paymentRepository` (inyectar el `@Autowired` si falta) → `GET /pago/exito` → assert `/notifications` del creador contiene `"reserv"` y `/notifications` del huésped contiene `"confirmada"`. **Eliminar el `POST /reservations/{id}/confirm`** de ese test (ya no hace falta: el pago confirma).

- [ ] **Step 8: Fijar el webhook `expired` (Review Focus 2)**

En `StripeWebhookControllerTests.expiredFallaElPagoYLiberaCupo` (línea 171), tras el POST del webhook, agregar: la reserva de ese fixture queda `ReservationStatus.CANCELADO` y **no** `CONFIRMADA` (buscarla por `reservationRepository` — inyectarlo si falta — o por `findByInstanceIdAndStatus(...)`).

- [ ] **Step 9: Correr los tests afectados**

Run: `./mvnw test -Dtest=PaymentSuccessFlowTests,CancellationFlowTests,StripeWebhookControllerTests,StripeCheckoutServiceTest`
Expected: PASS.

- [ ] **Step 10: Suite completa**

Run: `./mvnw test`
Expected: `BUILD SUCCESS` (301 + 3 nuevos = 304).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/openlab/mx/share/payment/service/StripeCheckoutService.java \
        src/main/java/com/openlab/mx/share/payment/controller/CheckoutController.java \
        src/main/java/com/openlab/mx/share/reservation/repository/ReservationRepository.java \
        src/test/java/com/openlab/mx/share/PaymentSuccessFlowTests.java \
        src/test/java/com/openlab/mx/share/CancellationFlowTests.java \
        src/test/java/com/openlab/mx/share/payment/controller/StripeWebhookControllerTests.java \
        src/test/java/com/openlab/mx/share/payment/service/StripeCheckoutServiceTest.java
git commit -m "feat(StripeCheckoutService): confirmar la reserva al pagar y notificar a los dos"
```

(cuerpo: transición en `complete()`, avisos a ambos, aviso previo eliminado)

---

### Task 2: Eliminar la confirmación manual del creador

Depende de Task 1 (sin ella, quitar la confirmación manual dejaría reservas eternamente `PENDIENTE`).

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/reservation/controller/ReservationController.java` (método `confirm` ~L66-76, atributo `pendientes` ~L81-83, campos/imports sin uso)
- Modify: `src/main/java/com/openlab/mx/share/reservation/service/ReservationService.java` (método `confirm` ~L74-85)
- Modify: `src/main/resources/templates/reservations/my-reservations.html` (sección "Esperando tu confirmación", ~L19-33)
- Test: `src/test/java/com/openlab/mx/share/ReservationFlowTests.java` (2 tests nuevos)
- Modify: `src/test/java/com/openlab/mx/share/reservation/service/ReservationServiceTest.java` (borrar `testConfirmSoloElDueno`)
- Modify: `src/test/java/com/openlab/mx/share/chat/ChatStompTests.java` (línea 141)
- Modify: `src/test/java/com/openlab/mx/share/E2eSmokeTests.java` (paso 4, líneas ~109-112)

**Interfaces:**
- Consumes: Task 1 (reserva `CONFIRMADA` al pagar).
- Produces: `ReservationService` ya **no tiene** `confirm(Long, User)`; `POST /reservations/{id}/confirm` no existe (404); `myReservations()` no expone `pendientes`.

- [ ] **Step 1: Escribir los tests rojos**

En `ReservationFlowTests` (mismo patrón de auth de la clase, `TestGatewayConfig` ya importado):

```java
@Test
void confirmacionManualYaNoExiste() throws Exception {
    mockMvc.perform(post("/reservations/1/confirm")
                    .with(user("demo_creator").roles("CREATOR")).with(csrf()))
            .andExpect(status().isNotFound());
}

@Test
@WithMockUser("demo_user")
void misReservasNoMuestraLaColaDeConfirmacion() throws Exception {
    mockMvc.perform(get("/reservations"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Esperando tu confirmación"))));
}
```

(agregar imports `not`/`containsString`/`post` si faltan).

- [ ] **Step 2: Correr y verificar el rojo**

Run: `./mvnw test -Dtest=ReservationFlowTests`
Expected: FAIL — el POST devuelve 3xx (endpoint aún existe) y la cola sigue renderizándose (solo si hay `pendientes`; con BD vacía el `th:if` la oculta, por eso el primer test es el ancla del rojo).

- [ ] **Step 3: Eliminar el flujo manual**

- Borrar `ReservationController.confirm(...)` y `model.addAttribute("pendientes", ...)`; limpiar lo que quede sin uso: campo `notificationService`, imports `PreAuthorize` y `ReservationStatus`.
- Borrar `ReservationService.confirm(...)` + su `@Transactional` dedicado y el import de `NotOwnerException` si quedó sin uso.
- Borrar la sección `<section th:if="${!#lists.isEmpty(pendientes)}">…</section>` de `my-reservations.html`.

- [ ] **Step 4: Ajustar los 3 tests que dependían del flujo manual**

- `ReservationServiceTest`: borrar el método `testConfirmSoloElDueno` (líneas ~175-192) — el método ya no existe.
- `ChatStompTests` (helper `fechaConfirmada()`, línea 141): reemplazar `reservationService.confirm(reserva.getId(), usuario("demo_creator"))` por sembrado directo — `reserva.setStatus(ReservationStatus.CONFIRMADA); reservationRepository.save(reserva);` (el repositorio ya está inyectado en la clase).
- `E2eSmokeTests` paso 4 (líneas ~109-112): borrar el bloque `POST /reservations/{id}/confirm` — su `/pago/exito` anterior ya deja la reserva `CONFIRMADA` (Task 1). Los asserts de escrow posteriores no cambian.

- [ ] **Step 5: Correr la suite completa (compilación + verde)**

Run: `./mvnw test`
Expected: `BUILD SUCCESS` — cualquier referencia olvidada a `confirm()` revienta la compilación acá.

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/com/openlab/mx/share/reservation src/main/resources/templates/reservations \
        src/test/java/com/openlab/mx/share/ReservationFlowTests.java \
        src/test/java/com/openlab/mx/share/reservation/service/ReservationServiceTest.java \
        src/test/java/com/openlab/mx/share/chat/ChatStompTests.java \
        src/test/java/com/openlab/mx/share/E2eSmokeTests.java
git commit -m "refactor(ReservationController): eliminar la confirmación manual de reservas"
```

---

### Task 3: Aviso del chat correcto para reservas sin pagar

**Files:**
- Test: `src/test/java/com/openlab/mx/share/reservation/PostPagoConfirmationTests.java` (`pendienteMuestraAvisoSinLinkDeChat`)
- Modify: `src/main/resources/templates/confirmation.html`
- Modify: `src/main/java/com/openlab/mx/share/reservation/controller/ReservationController.java` (`confirmation()`)

**Interfaces:**
- Consumes: Task 2 (ya no existe confirmación manual → el copy viejo miente).
- Produces: modelo de `/confirmation` sin `nombreCreador`; hint fijo `"El chat grupal se abre cuando se complete el pago."`.

- [ ] **Step 1: Actualizar el test al copy nuevo (rojo)**

En `pendienteMuestraAvisoSinLinkDeChat`: cambiar `containsString("se abre cuando")` por `containsString("cuando se complete el pago")` y **borrar** el `containsString(creador.getUsername())` (el creador ya no aparece en el aviso). El resto (sin link `/chats/{id}`, con `calendar.ics`) se mantiene.

- [ ] **Step 2: Correr y verificar el rojo**

Run: `./mvnw test -Dtest=PostPagoConfirmationTests`
Expected: FAIL — el template viejo dice "cuando {creador} confirme".

- [ ] **Step 3: Corregir template y modelo**

- `confirmation.html`: el `<p class="hint">` pasa a `El chat grupal se abre cuando se complete el pago.` (sin `<strong>` del creador).
- `ReservationController.confirmation()`: borrar `model.addAttribute("nombreCreador", ...)`.

- [ ] **Step 4: Correr en verde**

Run: `./mvnw test -Dtest=PostPagoConfirmationTests`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/confirmation.html \
        src/main/java/com/openlab/mx/share/reservation/controller/ReservationController.java \
        src/test/java/com/openlab/mx/share/reservation/PostPagoConfirmationTests.java
git commit -m "fix(confirmation): avisar que el chat se abre al completar el pago"
```

---

### Task 4: Migración V3 de backfill (reservas históricas pagadas)

**Files:**
- Create: `src/main/resources/db/migration/V3__instant_book_backfill.sql`
- Test: `src/test/java/com/openlab/mx/share/reservation/InstantBookBackfillMigrationTests.java`

**Interfaces:**
- Consumes: esquema existente (`reservations.status`, `payments.status` como `VARCHAR` con enum `STRING`) y `V2__stripe_connect.sql` (la V3 es la siguiente libre).
- Produces: al arrancar, toda `PENDIENTE` con pago `HELD`/`RELEASED` queda `CONFIRMADA`.

- [ ] **Step 1: Escribir el test rojo**

```java
@SpringBootTest
class InstantBookBackfillMigrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test
    void backfillConfirmaSoloReservasPagadas() {
        // 1) fila A: reservation PENDIENTE + payment HELD   (debe confirmarse)
        // 2) fila B: reservation PENDIENTE + payment PENDING (control: queda igual)
        //    inserts con GeneratedKeyHolder; columns: reservations(attendees, created_at, status,
        //    instance_id, user_id) y payments(amount, commission, status, reservation_id)
        // 3) ejecutar el archivo de la clasepath:
        //    new ClassPathResource("db/migration/V3__instant_book_backfill.sql")
        //    → leer texto → jdbc.execute(sql)
        // 4) asserts: A.status == "CONFIRMADA"; B.status == "PENDIENTE"
        // 5) limpiar: DELETE payments y reservations por los ids capturados (@AfterEach o finally)
    }
}
```

Los asserts leen el `status` crudo con `jdbc.queryForObject("select status from reservations where id=?", String.class, idA)`.

- [ ] **Step 2: Correr y verificar el rojo**

Run: `./mvnw test -Dtest=InstantBookBackfillMigrationTests`
Expected: FAIL — el archivo `V3__instant_book_backfill.sql` no existe (`ClassPathResource` → error de lectura).

- [ ] **Step 3: Crear la migración**

`src/main/resources/db/migration/V3__instant_book_backfill.sql`:

```sql
UPDATE reservations
   SET status = 'CONFIRMADA'
 WHERE status = 'PENDIENTE'
   AND EXISTS (SELECT 1 FROM payments
                WHERE payments.reservation_id = reservations.id
                  AND payments.status IN ('HELD', 'RELEASED'));
```

Sin alias en el `UPDATE` y sin funciones específicas de motor: corre igual en Postgres e H2. Idempotente (una segunda corrida no encuentra `PENDIENTE` pagadas).

- [ ] **Step 4: Correr el test y la suite completa**

Run: `./mvnw test -Dtest=InstantBookBackfillMigrationTests && ./mvnw test`
Expected: PASS — la suite completa además prueba que Flyway ejecuta la V3 al arrancar el H2 de tests (si la sintaxis fallara, **todos** los contextos revientan).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V3__instant_book_backfill.sql \
        src/test/java/com/openlab/mx/share/reservation/InstantBookBackfillMigrationTests.java
git commit -m "feat(V3__instant_book_backfill.sql): confirmar reservas pagadas existentes"
```
