# Stripe Checkout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el checkout simulado por pagos reales con Stripe Checkout Sessions (manual capture), conservando el dominio de escrow/5%/reembolsos intacto.

**Architecture:** Feature payments existente (`com.openlab.mx.share.payment`) se extiende con un puerto `PaymentGateway` + adapter Stripe + fake de test. `ReservationService` deja de crear el pago (HELDEado); la orquestación checkout pasa a `StripeCheckoutService`: reserva PENDIENTE + `Payment PENDING` → session Stripe → confirmación (retorno o webhook) → HELD. Escrow y cancelaciones pasan a llamar al gateway tras actualizar DB.

**Tech Stack:** Spring Boot 4.1.1 (Java 25), Thymeleaf, `spring-boot-starter-security` (form login + CSRF), `ddl-auto=update` (sin Flyway), Lombok, JUnit5+Mockito+MockMvc, `com.stripe:stripe-java:34.0.0`.

**Spec:** `docs/superpowers/specs/2026-10-14-stripe-checkout-design.md` (autoridad; el plan argumenta desde ella).

## Global Constraints

- Monto y moneda SIEMPRE desde el dominio: `experience.price × attendees` (unit_amount en centavos, `× 100`), `experience.currencyCode` default `MXN`. El front nunca manda `amount`.
- `POST /checkout/{instanceId}` idempotente ante doble submit: si existe reserva PENDIENTE + payment PENDING con `stripeSessionId`, reusar la MISMA sesión (no crear otra).
- Webhook idempotente por `event_id` en tabla `stripe_event`; llamadas de captura/cancel con Idempotency-Key de Stripe.
- Ningún PAN/CVC/SK/webhook-secret en logs ni en el front.
- Sin keys (`stripe.secret-key` vacío): el sistema compila y TODOS los tests pasan con el fake; el POST de checkout falla con mensaje claro ("El pago no está disponible ahora: Stripe no está configurado") — nunca NullPointerException.
- UX conservada: sanitización de attendees 1..20, errores por `addFlashAttribute`, final PRG a `/confirmation` (vista y cálculo `monto/payout` de `ReservationController.confirmation` NO cambian).
- Escrow sin cambios: `commission = amount × 5%` (`escrow.commission-rate`, default 0.05), auto-release 2 días (`escrow.auto-release-days`), refund 100% >24h (default `partial-refund-rate=0.5`). `heldAt` se setea al pasar PENDING→HELD (nunca al crear). DB es source of truth del estado; Stripe aplica el efecto del dinero.
- Pagos legacy sin `stripePaymentIntentId`: skip de llamada a gateway (comportamiento contable igual al actual).
- Convenciones del repo: commits en castellano estilo Conventional (`feat:`, `test:`, `refactor:`), comentario de clases con referencia a spec §, excepciones de dominio con mensaje en castellano.

## Review Focus

1. **Doble submit del POST /checkout** → no duplica reserva/pago/sesión. Test: Task 5 (paso de reintento reusa `cs_fake_1`).
2. **Checkout abandonado** (`checkout.session.expired`) → payment FAILED y reserva CANCELADO (cupo libre). Test: Task 6 (webhook expired).
3. **Webhook desconocido o con firma inválida** → inválida 400; desconocido 200 sin efecto de estado. Test: Task 6.
4. **Retorno revalidado contra Stripe, no contra el query param** → `GET /pago/exito?session_id=X` consulta `authorizeInfo(X)`; usuario ajeno no completa el pago de otro. Test: Task 5 (dueño ajeno + `markUnpaid`).
5. **Pago sin keys** → error claro, no NPE. Test: Task 3 (`UnconfiguredPaymentGateway` lanza `PaymentUnavailableException`).

---

### Task 1: Estados y columnas de Stripe en `Payment`

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/payment/entity/PaymentStatus.java`
- Modify: `src/main/java/com/openlab/mx/share/payment/entity/Payment.java`
- Test: `src/test/java/com/openlab/mx/share/payment/entity/PaymentStatusTest.java`

**Interfaces:**
- Consumes: nada nuevo.
- Produces: `PaymentStatus.PENDING`, `PaymentStatus.FAILED`; `Payment.stripeSessionId`, `Payment.stripePaymentIntentId` (String nullable, getter/setter Lombok).

- [ ] **Step 1: Write the failing test**

```java
// PaymentStatusTest.java — paquete com.openlab.mx.share.payment.entity
// assert true: EnumSet.allOf(PaymentStatus.class) contiene HELD, RELEASED,
// REFUNDED_FULL, REFUNDED_PARTIAL, PENDING, FAILED (los 6, nada más).
// assert true: Payment.builder() (con dotación mínima: reservation null-safe no requerida)
// acepta .stripeSessionId("cs_test_x").stripePaymentIntentId("pi_test_y") y los
// getters devuelven exactamente esos valores; al no setearlos, null.
```

- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=PaymentStatusTest` → FAIL (valores/columnas no existen)
- [ ] **Step 3: Implement** — agregar `PENDING, FAILED` al enum; agregar a `Payment` `@Column(name="stripe_session_id") private String stripeSessionId;` y `@Column(name="stripe_payment_intent_id") private String stripePaymentIntentId;`. Javadoc de clase: referencia a spec §3.
- [ ] **Step 4: Run** `./mvnw -q test -Dtest=PaymentStatusTest` → PASS
- [ ] **Step 5: Commit** `git add ... && git commit -m "feat: estados PENDING/FAILED y columnas stripe en Payment (spec §3.1-3.2)"`

### Task 2: Puerto `PaymentGateway` + `FakePaymentGateway`

**Files:**
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/PaymentGateway.java`
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/GatewayCheckoutRequest.java`
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/GatewaySession.java`
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/FakePaymentGateway.java`
- Create: `src/main/java/com/openlab/mx/share/shared/exception/PaymentUnavailableException.java`
- Test: `src/test/java/com/openlab/mx/share/payment/gateway/FakePaymentGatewayTest.java`

**Interfaces:**
- Consumes: nada.
- Produces (Task 3-8 dependen de estas firmas EXACTAS):

```java
public interface PaymentGateway {
    GatewaySession createCheckout(GatewayCheckoutRequest request);
    GatewaySession authorizeInfo(String sessionId);
    void capture(String paymentIntentId);
    void capturePartial(String paymentIntentId, java.math.BigDecimal amount);
    void cancel(String paymentIntentId);
}
// record GatewaySession(String sessionId, String url, String paymentIntentId, String status)
// record GatewayCheckoutRequest(java.math.BigDecimal unitAmount, String currency,
//        int quantity, String description, java.util.Map<String,String> metadata)   // monto unitario × quantity = total
```

- `PaymentUnavailableException extends DomainException` (castellano: "El pago no está disponible ahora: Stripe no está configurado").
- `FakePaymentGateway implements PaymentGateway`: estado in-memory `Map<String, ModeloFalso>`. `createCheckout` genera `cs_fake_<n>` secuencial, `url = /__fake_stripe_/checkout/" + sessionId`, `paymentIntentId = "pi_fake_<n>"`, status inicial `open`; loguea y aplica `metadata` sin validar montos. `authorizeInfo(sessionId)` → status `paid` **por defecto**; helper package-private `markUnpaid(String sessionId)` para tests. `capture/capturePartial/cancel` marcan estado y guardan el monto de la llamada parcial (helper `capturedAmountOf(...)`) para asserts. Sin bean de producción (solo se inyecta en tests).

- [ ] **Step 1: Write the failing test** — `FakePaymentGatewayTest`: (a) `createCheckout` devuelve sessionId `cs_fake_1` luego `cs_fake_2` en segunda llamada, url no nula, pi id; (b) `authorizeInfo` → `paid`; tras `markUnpaid` → `unpaid`; (c) sessionId desconocido en `authorizeInfo` → `NoSuchElementException`; (d) `capture`/`cancel`/`capturePartial(amount)` registran la operación consultable por helpers.
- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=FakePaymentGatewayTest` → FAIL (clases no existen)
- [ ] **Step 3: Implement** los 5 archivos con las firmas de arriba.
- [ ] **Step 4: Run** `./mvnw -q test -Dtest=FakePaymentGatewayTest` → PASS
- [ ] **Step 5: Commit** `git commit -m "feat: puerto PaymentGateway + FakePaymentGateway (spec §5)"`

### Task 3: Adapter `StripePaymentGateway` + config

**Files:**
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/StripePaymentGateway.java`
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/UnconfiguredPaymentGateway.java`
- Create: `src/main/java/com/openlab/mx/share/payment/gateway/PaymentGatewayConfig.java`
- Modify: `pom.xml` (dependencia `com.stripe:stripe-java:34.0.0`)
- Modify: `src/main/resources/application.properties` (agregar `stripe.secret-key=${STRIPE_SECRET_KEY:}`, `stripe.publishable-key=${STRIPE_PUBLISHABLE_KEY:}`, `stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}`, `app.base-url=${APP_BASE_URL:http://localhost:8080}`)
- Test: `src/test/java/com/openlab/mx/share/payment/gateway/StripeAmountsTest.java`

**Interfaces:**
- Consumes: puerto de Task 2.
- Produces: bean `PaymentGateway paymentGateway(String secretKey)` — si key vacía/blank → `UnconfiguredPaymentGateway` (todos los métodos lanzan `PaymentUnavailableException`); si key presente → `StripePaymentGateway`. Produces también `static long toCents(java.math.BigDecimal)` en `StripePaymentGateway`.

- [ ] **Step 1: Write the failing test** — `StripeAmountsTest`: `toCents(new BigDecimal("380.00")) == 38000L`; `toCents(new BigDecimal("19.05")) == 1905L`; `toCents(new BigDecimal("0.99")) == 99L`. Setup de `StripePaymentGateway` sin llamar a la API: `UnconfiguredPaymentGateway` lanza `PaymentUnavailableException` en `createCheckout`/`capture`/`cancel`/`capturePartial`/`authorizeInfo` (5 asserts).
- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=StripeAmountsTest` → FAIL
- [ ] **Step 3: Implement**:

```java
// Magic: buildBean paymentGateway(@Value("${stripe.secret-key:}") String key)
//   key == null || key.isBlank() → new UnconfiguredPaymentGateway()
//   else → new StripePaymentGateway(key)   // única clase que importa el SDK com.stripe.*
// StripePaymentGateway.createCheckout: SessionCreateParams
//   .setMode(MODE_PAYMENT), .setSuccessUrl(baseUrl+"/pago/exito?session_id={CHECKOUT_SESSION_ID}")
//   .setCancelUrl(baseUrl+"/checkout/"+metadata.get("instanceId")+"?attendees="+metadata.get("attendees"))
//   payment_intent_data: .setCaptureMethod(MANUAL), metadata completo
//   line_items: price_data(currency=q.currency.toLowerCase(), unit_amount=toCents(q.unitAmount),
//               product_data.name=q.description), quantity=q.quantity
// authorizeInfo: Session.retrieve(sessionId) → GatewaySession(sid, url, paymentIntent, payment_status)
// capture: PaymentIntent.retrieve(id).capture()  | capturePartial: pi.capture(...) amount=toCents | cancel: pi.cancel()
// ambas llamadas de capture con RequestOption Idempotency-Key = `<paymentIntentId>:capture|capturePartial`
// Nota: los nombres de método del SDK 34 ("setCaptureMethod", "MANUAL") se verifican contra el javadoc local;
// si cambió la forma de construir params en 34.x, el implementer mapea al equivalente actual y lo documenta en javadoc (spec §8).
```

- [ ] **Step 4: Run** `./mvnw -q test -Dtest=StripeAmountsTest` → PASS (los asserts de `UnconfiguredPaymentGateway` van en el mismo archivo de test)
- [ ] **Step 5: Commit** `git commit -m "feat: adapter Stripe PaymentGateway con manual capture y fallback sin keys (spec §2, §3, §8)"`

### Task 4: `StripeCheckoutService` (orquestación) + `ReservationService` refactor

**Files:**
- Create: `src/main/java/com/openlab/mx/share/payment/service/StripeCheckoutService.java`
- Modify: `src/main/java/com/openlab/mx/share/reservation/service/ReservationService.java:52-88` (reserve() deja de crear Payment; se queda con reserva `PENDIENTE` única; test correspondiente adaptado)
- Modify: `src/main/java/com/openlab/mx/share/payment/repository/PaymentRepository.java` (agregar `Optional<Payment> findFirstByStripeSessionId(String stripeSessionId);`)
- Test: `src/test/java/com/openlab/mx/share/payment/service/StripeCheckoutServiceTest.java`
- Test: Modify `src/test/java/com/openlab/mx/share/reservation/service/ReservationServiceTest.java` (ya no espera `Payment` HELD al reservar)

**Interfaces:**
- Consumes: puerto de Task 2; en producción el bean `paymentGateway(...)` de Task 3 (con keys vacías → Unconfigured lanza). En tests se inyecta `FakePaymentGateway`.
- Produces (Task 5-6 usan estas firmas):

```java
// StripeCheckoutService (@Service, inyecta ReservationService, PaymentRepository, PaymentGateway):
//   GatewaySession start(Long instanceId, int attendees, User user, String baseUrl)
//     — crea/reusa reserva PENDIENTE y payment PENDING; crea la sesión (o reusa la existente
//       con doble submit) y retorna la GatewaySession (el controller deduce redirect:" + gs.url())
//   Payment complete(String sessionId, Long userId)
//     — authorizeInfo(sessionId): solo status "paid" pasa; PENDING→HELD, guarda stripePaymentIntentId + heldAt=now;
//       userId ≠ reservation.user → NotOwnerException; si ya HELD → retorna payment sin efecto (idempotente).
//   void expire(String sessionId)
//     — PENDING→FAILED + reserva→CANCELADO; HELD/RELEASED/FAILED → no-op.
```

- Registro en `start`: `amount = price × attendees` (scale 2), `commission = amount × escrow.commission-rate`, `status PENDING`, reserva PENDIENTE asociada (invoca `reservationService.reserve(...)`, que ya no crea el payment), y `stripeSessionId` guardado tras crear la sesión (si `gateway.createCheckout` lanza → rollback: payment no persistido; el controller hace flash del error).
- `GatewayCheckoutRequest`: `unitAmount = price`, `currency = experience.currencyCode ?: "MXN"`, `quantity = attendees`, `description = experience.name`, `metadata = {instanceId, userId, attendees, reservationId}`. Reintentos de `start` con misma reserva y su PENDING payment reusando `stripeSessionId` existente (idempotencia Global Constraint 2).

- [ ] **Step 1: Write the failing test** — `StripeCheckoutServiceTest` (Mockito para repos/servicios + `FakePaymentGateway` real): (a) `start` de precio 380 MXN con attendees=2 guarda payment `amount=380.00`, `commission=19.00` (tasa 0.05), `status=PENDING`, `stripeSessionId="cs_fake_1"`, `heldAt=null`; (b) doble submit (misma instancia+usuario) reusa `cs_fake_1` (no se crea segundo payment ni cs_fake_2); (c) `complete("cs_fake_1", ownerId)` → status HELD, `stripePaymentIntentId="pi_fake_1"`, `heldAt != null`; (d) `complete` repetida → idempotente (heldAt intacto); (e) `complete` con userId distinto → `NotOwnerException` y payment sigue PENDING; (f) `expire` sobre payment HELD → no-op; `expire` sobre PENDING → payment FAILED + reservation CANCELADO.
- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=StripeCheckoutServiceTest` → FAIL
- [ ] **Step 3: Implement** servicio (lógica de arriba) y refactor `ReservationService`: `reserve()` solo crea la reserva PENDIENTE con las validaciones de cupo/estado existentes (todo el código actual intacto salvo el bloque de creación del `Payment`, que se mueve a `StripeCheckoutService.start`).
- [ ] **Step 4: Adaptar `ReservationServiceTest`** — ya no hay `paymentRepository.save(...)`; asserts solo sobre Reservation PENDIENTE creada; pasan los casos de cupo/fecha.
- [ ] **Step 5: Run** `./mvnw -q test -Dtest=StripeCheckoutServiceTest,ReservationServiceTest` → PASS
- [ ] **Step 6: Commit** `git commit -m "feat: orquestación Stripe Checkout (reserva PENDIENTE + payment PENDING→HELD) (spec §5-6)"`

### Task 5: Web (controller + template) y flujo `/pago/exito`

**Files:**
- Rewrite: `src/main/java/com/openlab/mx/share/payment/controller/CheckoutController.java` — GET igual al actual; POST sin `CheckoutRequest`: `GatewaySession gs = stripeCheckoutService.start(instanceId, attendees, user, baseUrl)` → `return "redirect:" + gs.url();`. El bloque idempotencia + `_csrf` del form + `catch (RuntimeException e)` con `addFlashAttribute("error", ...)` se conserva (la idempotencia del doble submit vive ahora en el servicio).
- Create: `src/main/java/com/openlab/mx/share/payment/controller/PaymentSuccessController.java` — `GET /pago/exito?session_id=...`: revalida vía `stripeCheckoutService.complete(sessionId, currentUser.require().getId())` → PRG `redirect:/confirmation`. Si sesión no existe → 404 como DomainException actual. CSRF no aplica (GET).
- Rewrite: `src/main/resources/templates/checkout.html` — quitar campos de tarjeta + `CheckoutRequest`; head reemplaza título "Pago simulado" → "Pago seguro con Stripe"; botón submit "Pagar con Stripe" (único botón Submit, form POST clásico + `_csrf`).
- Delete: `src/main/java/com/openlab/mx/share/reservation/dto/CheckoutRequest.java`
- Test: Modify `src/test/java/com/openlab/mx/share/ReservationFlowTests.java` (POST /checkout ahora sin cardNumber/holder/cvv; assert redirect Location a url de la fake session; simular retorno con `GET /pago/exito?session_id=cs_fake_1` → Payment HELD)
- Test: Modify `src/test/java/com/openlab/mx/share/E2eSmokeTests.java:88` igual (menos card fields, sessionid de la fake determinística)
- Test: `src/test/java/com/openlab/mx/share/PaymentSuccessFlowTests.java` nuevo (ver Review Focus 1 y 4)

**Interfaces:**
- Consumes: `StripeCheckoutService` (Task 4), `FakePaymentGateway` bean de test.
- Produces: rutas `GET/POST /checkout/{instanceId}`, `GET /pago/exito?session_id=...`.

**Config de test compartido:**
- Create: `src/test/java/com/openlab/mx/share/shared/TestGatewayConfig.java` — `@TestConfiguration` con `@Bean @Primary PaymentGateway fakePaymentGateway()` (se usa en las clases MockMvc con `@SpringBootTest` + `@Import`):

- [ ] **Step 1: Write the failing test** — `PaymentSuccessFlowTests`:
  - (a) POST /checkout autenticado → 302 Location conteniendo `/__fake_stripe_/checkout/cs_fake_1`;
  - (b) reintento POST → Location sigue `cs_fake_1` (no cs_fake_2): Review Focus 1;
  - (c) GET /pago/exito?session_id=cs_fake_1 con el dueño → 302 /confirmation y `Payment.status == HELD`;
  - (d) GET /pago/exito con otro user → `NotOwnerException` (403 path actual del handler de DomainException) y payment permanece PENDING;
  - (e) GET /pago/exito?session_id=cs_inexistente → DomainException 404 respuesta existente.
- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=PaymentSuccessFlowTests` → FAIL
- [ ] **Step 3: Implement** controllers + template + refactor; eliminar `CheckoutRequest.java` (verificar con `grep -rn CheckoutRequest src/` que no queda referencia).
- [ ] **Step 4: Run** `./mvnw -q test -Dtest=PaymentSuccessFlowTests` → PASS
- [ ] **Step 5: Adaptar** `ReservationFlowTests.java` y `E2eSmokeTests.java` al flujo nuevo (posteos idénticos, sin campos de tarjeta; agregan `@Import(TestGatewayConfig.class)` si no lo tenían); correr: `./mvnw -q test -Dtest=ReservationFlowTests,E2eSmokeTests` → PASS
- [ ] **Step 6: Commit** `git commit -m "feat: checkout con redirect a Stripe y retorno /pago/exito (spec §6)"`

### Task 6: Webhook `/api/stripe/webhooks` + idempotencia + SecurityConfig

**Files:**
- Create: `src/main/java/com/openlab/mx/share/payment/entity/StripeEvent.java` (entity: `event_id` PK String, `type`, `processedAt`; tabla `stripe_event`)
- Create: `src/main/java/com/openlab/mx/share/payment/repository/StripeEventRepository.java`
- Create: `src/main/java/com/openlab/mx/share/payment/controller/StripeWebhookController.java`
- Modify: `src/main/java/com/openlab/mx/share/shared/config/SecurityConfig.java` — permitAll + CSRF-ignoring ONLY de `/api/stripe/webhooks`
- Test: `src/test/java/com/openlab/mx/share/payment/controller/StripeWebhookControllerTests.java`

**Interfaces:**
- Consumes: `StripeCheckoutService.complete/expire` (Task 4), `PaymentRepository.findFirstByStripeSessionId`, `findFirstByStripePaymentIntentId` (agregar este método acá).
- Produces: endpoint público `POST /api/stripe/webhooks`.

- [ ] **Step 1: Write the failing test** — MockMvc (Webhook body crudo + header `Stripe-Signature`):

```java
// Firma válida se genera con el helper del propio endpoint Nano — implementar util de test:
//   String firma Stripe-Signature válida para (payload, secret) =
//     "t=" + ts + ",v1=" + hmacSHA256Hex(secret, ts + "." + payload)
//   (esquema oficial de Stripe, computable en tests sin SDK)
// Tests: (a) checkout.session.completed con firma válida y session_id=cs_fake_1, event_id=evt_1
//           → 200, payment PENDING→HELD con stripePaymentIntentId del fake, y fila evt_1 en stripe_event;
//         (b) mismo payload reenviado (evt_1) → 200 y payment sin doble efecto (idempotencia: el estado queda HELD, heldAt sin cambios);
//         (c) firma inválida ("v1=deadbeef") → 400, nada persistido;
//         (d) checkout.session.expired (evt_2, cs_fake_2 con payment PENDING) → payment FAILED + reservation CANCELADO;
//         (e) tipo desconocido customer.x.y (evt_3) → 200, sin efecto de estado;
//         (f) payment_intent.payment_failed (evt_4) → payment HELD/PENDING → FAILED
```

- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=StripeWebhookControllerTests` → FAIL
- [ ] **Step 3: Implement**: controller (`@RequestBody String payload`, header `Stripe-Signature`, `Webhook.constructEvent(payload, sigHeader, webhookSecret)`; firma inválida → `ResponseEntity 400`), idempotencia por `stripeEventRepository.existsById(eventId)` guardando el evento ANTES de aplicar el efecto, switch por tipo de evento → 200. `SecurityConfig` permitAll + `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/stripe/webhooks"))`.
- [ ] **Step 4: Run** `./mvnw -q test -Dtest=StripeWebhookControllerTests` → PASS
- [ ] **Step 5: Commit** `git commit -m "feat: webhook Stripe con firma e idempotencia por event_id (spec §7)"`

### Task 7: Wiring escrow/cancelaciones → gateway

**Files:**
- Modify: `src/main/java/com/openlab/mx/share/payment/service/EscrowService.java:35-36` (`tryRelease` captura real: tras persistir RELEASED → `gateway.capture(payment.getStripePaymentIntentId())` si no-null, MISMA transacción). Inyectar `PaymentGateway`.
- Modify: `src/main/java/com/openlab/mx/share/reservation/service/CancellationService.java` (`reembolsar`): payment HELD con pi no-null → total → `gateway.cancel(pi)`; parcial → `gateway.capturePartial(pi, refund)`; pi null (legacy) → skip contable igual que hoy.
- Test: Modify `src/test/java/com/openlab/mx/share/payment/service/EscrowServiceTest.java` (mock de `PaymentGateway`; assert `capture("pi_test_x")` llamado exactamente 1 vez al liberar)
- Test: Modify `src/test/java/com/openlab/mx/share/reservation/service/CancellationServiceTest.java` (assert `cancel(pi)` en refund total, `capturePartial(pi, 190.00)` en refund parcial de 380, y sin llamada si `stripePaymentIntentId=null`)
- Test: `src/test/java/com/openlab/mx/share/payment/service/EscrowPiNullTest.java` (o dentro de EscrowServiceTest): payment legacy RELEASED con pi=null no invoca el gateway.

**Interfaces:**
- Consumes: puerto Task 2.
- Produces: comportamiento observado de escrow/cancelaciones con efecto monetario via gateway.

- [ ] **Step 1: Write the failing test** — asserts de arriba; la aserción 100%/50% del monto `refund` persiste como hoy.
- [ ] **Step 2: Run failing** `./mvnw -q test -Dtest=EscrowServiceTest,CancellationServiceTest` → FAIL (llamadas esperadas no ocurren)
- [ ] **Step 3: Implement** wiring en ambos servicios (la persistencia del estado queda donde está; el gateway se invoca tras el `save` de DB en la misma transacción).
- [ ] **Step 4: Run** `./mvnw -q test -Dtest='EscrowServiceTest,CancellationServiceTest,EscrowReleaseServiceTest,ScheduledJobsTest'` → PASS (los jobs no tocan gateway dado que delegan en escrowService)
- [ ] **Step 5: Commit** `git commit -m "feat: escrow y cancelaciones aplican el efecto en Stripe via PaymentGateway (spec §4)"`

### Task 8: Suite completa + verificación

**Files:**
- Modify: `pom.xml` si algún test demanda spring-security-test en forma nueva (NO debería).
- Test: NONE nuevos.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: build y suite verdes con sin env vars de Stripe.

- [ ] **Step 1: Run suite completa** `./mvnw test` (verificar exit code 0 y resumen de fallos): PASS esperado con todos los tests existentes adaptados y los nuevos verdes.
- [ ] **Step 2: Cross-check Review Focus:** correr todos los tests de Review Focus (Tasks 3/5/6 incluyen los 5). Si un test de la lista quedó sin dueño, escribirlo acá (Task 8).
- [ ] **Step 3: Compilación lint clean:** `./mvnw -q compile` sin warnings nuevos de imports sin usar.
- [ ] **Step 4: Commit final** si hay leftover (si Task 1-7 dejaron archivos sin commit, `git status` limpio al terminar).

---

## Cross-task dependency map

- Task 1 → Task 4 (campos y estados); Task 2 → Tasks 3-7 (puerto); Task 3 → Task 4 (bean de producción; tests inyectan el fake); Task 4 → Tasks 5-6 (servicio de checkout); Tasks 5,6,7 → Task 8 (suite).
- Aviso de riesgo: los tests MVC (`@SpringBootTest`) NUNCA llaman a la API real de Stripe — toda la suite usa `FakePaymentGateway` (o `UnconfiguredPaymentGateway` para el caso sin keys).
