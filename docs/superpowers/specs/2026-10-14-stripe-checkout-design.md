# Spec — Pago de experiencias con Stripe Checkout

**Fecha:** 2026-10-14 · **Actualizado:** 2026-10-20 (§1 Connect, §3 esquema) · **Estado:** aprobada en chat por el usuario (diseño íntegro) · **Base:** `docs/superpowers/specs/2026-10-07-shared-web-core-design.md` (§5.3 pago simulado, §5.4 cancelaciones)

## 1. Objetivo

Reemplazar el checkout simulado (formulario de tarjeta que solo valida formato) por pagos reales con **Stripe Checkout Sessions** en modo **manual capture**. El dominio de escrow existente (`Payment`/`PaymentStatus`, comisión 5%, ventana de liberación, reembolsos por cancelación) **no cambia**: Stripe pasa a ser el mecanismo por el que el dinero entra, se retiene y se devuelve.

Fuera de alcance: facturación, live mode, PayPal/MercadoPago.

**Actualización 2026-10-20 — Stripe Connect ya está implementado** (dejó de estar "fuera de alcance"): cuentas Connect del creador con onboarding alojado (`StripeConnectService`, `ConnectAccount`, `ConnectPayout`, página `creators/onboarding`) y payout al creador con comisión del 5%. El país de cobro se valida contra la lista blanca de Stripe (`app.stripe.payout-countries`): Stripe Connect no soporta todos los países. Sigue fuera de alcance la **live mode**.

## 2. Decisión de modo (aprobada por el usuario)

- **Checkout Sessions (redirect)**: cero PCI, Apple/Google Pay incluido, mínimo JS. `window.location = session.url` desde el template.
- **Manual capture**: `payment_intent_data.capture_method=manual`. La autorización (HELD) no mueve dinero hasta que el dominio decide: capture total → RELEASED, cancel → REFUNDED_FULL, capture parcial (50%) → REFUNDED_PARTIAL.
- Riesgo aceptado (documentado en el diseño aprobado): la autorización expira ~7 días según el emisor. Ventanas del dominio (liberación ≤ 2 días desde HELD) entran holgadas. Si una autorización muere, `payment_intent.payment_failed` marca el pago FAILED y no hay cobro efectivo.

## 3. Datos

El proyecto usa **Flyway** como gestor del esquema (`db/migration`) con `ddl-auto=validate`: Hibernate no crea ni altera nada, solo comprueba que el esquema coincida con las entidades y falla al arrancar si hay drift. Las migraciones de Stripe son `V2__stripe_connect.sql` (tablas `connect_accounts` / `connect_payouts`, columna `payments.payout_status` e índices) y son **idempotentes** (`IF NOT EXISTS` en todo), porque se aplicaron también sobre bases donde las tablas ya existían. Compatibilidad con filas existentes (pagos simulados): las columnas de Stripe quedan `nullable`.

> **OJO — Spring Boot 4 y Flyway.** La autoconfiguración de Flyway vive en el módulo aparte `spring-boot-flyway`: con solo `flyway-core` en el pom **nadie dispara las migraciones** y el esquema queda en manos de Hibernate (que fue lo que pasaba antes de este cambio). El índice de `payments.payout_status` es **normal, no parcial**: era `WHERE payout_status = 'PENDING'` y H2 no soporta índices filtrados, así que las migraciones tienen que correr también contra el H2 de los tests.

### 3.1 `Payment` (modificar)

| Campo | Tipo | Notas |
|---|---|---|
| `stripeSessionId` | `String`, nullable | `cs_test_...` — idempotencia del reintento |
| `stripePaymentIntentId` | `String`, nullable | `pi_test_...` — capture/cancel/refund |

### 3.2 `PaymentStatus` (modificar)

Agrega `PENDING` (session creada, pago no completado) y `FAILED` (autorización muerta/expirada/declinada). El resto queda igual: `HELD → RELEASED | REFUNDED_FULL | REFUNDED_PARTIAL`, y ahora `PENDING → HELD | FAILED`.

Invariantes existentes no cambian: `heldAt` se setea al pasar a HELD (desde el retorno/webhook, no al crear); escrow solo actúa sobre HELD; RELEASED jamás refundable.

## 4. Mapeo escrow ↔ Stripe

| Evento de dominio | Acción de Stripe | Resultado |
|---|---|---|
| POST /checkout → crear sesión | `SessionCreate` con `capture_method=manual` | Payment `PENDING` |
| Retorno `payment_status=paid` o webhook `checkout.session.completed` | revalidar con `Session.retrieve` (query param NO confiable) | `PENDING → HELD` (guarda `pi_id`) |
| `EscrowService` libera (todos evaluaron / ventana vencida) / `EscrowReleaseService` job | `PaymentIntent.capture` (con Idempotency-Key) | `RELEASED` (payout contable, sin Connect) |
| Cancelación >24h (refund 90%) | `PaymentIntent.capture(amount=10%)` | `REFUNDED_PARTIAL`, `refund=90%` (la plataforma retiene el 10%) |
| Cancelación ≤24h (refund 50%) | `PaymentIntent.capture(amount=50%)` | `REFUNDED_PARTIAL`, `refund=50%` (la penalidad queda en la plataforma) |
| Cancelación de fecha por creador / cupo mínimo | `PaymentIntent.cancel` para cada pago HELD | `REFUNDED_FULL` |
| Webhook `checkout.session.expired` | — | payment `PENDING → FAILED`, reserva → `CANCELADO` (libera el cupo) |
| Webhook `payment_intent.payment_failed` (pago HELD muerto) | — | `HELD → FAILED` |

Comisión y payout **no cambian**: `commission = amount × 5%` se calcula al reservar; `payout = amount - commission` al liberar (contable).

## 5. Arquitectura (feature payments, convención del repo)

Paquete `com.openlab.mx.share.payment` (ya existe, se extiende — no se crean paquetes globales).

- **Puerto** (nuevo): `PaymentGateway` — `createCheckout(GatewayCheckoutRequest): GatewaySession`, `authorizeInfo(sessionId)`, `capture(paymentIntentId)`, `cancel(paymentIntentId)`, `capturePartial(paymentIntentId, Money)`.
- **Adapter** (nuevo): `StripePaymentGateway` — único archivo que habla con Stripe SDK. Regla de montos: el back calcula SIEMPRE el monto desde la experiencia × attendees (nunca monto del front); moneda = `experience.currencyCode`, default `MXN`; Stripe usa centavos (`× 100`).
- **Doble de test** (nuevo): `FakePaymentGateway` con estado in-memory para tests de dominio y de flujo.
- **Use case** (nuevo): `StripeCheckoutService` — orquesta: validar/crear `Reservation` PENDIENTE idempotente, crear `Payment` PENDING, crear sesión, guardar `stripeSessionId`, devolver URL. Y el retorno: revalidar sesión en Stripe (`authorizeInfo`) + `PENDING → HELD` idempotente.
- **Use case** (modificar): `EscrowService` (release) / `EscrowReleaseService` / `CancellationService.reembolsar` — llaman al gateway tras actualizar el estado en DB (DB sigue siendo la source of truth del estado; la llamada a Stripe aplica el efecto real del dinero).

## 6. Web (Thymeleaf) y rutas

| Ruta | Estado | Flujo |
|---|---|---|
| `GET /checkout/{instanceId}?attendees=N` | rewrite | resumen (fecha, cupos, total, moneda) + botón **Pagar con Stripe** — sin formulario de tarjeta |
| `POST /checkout/{instanceId}` | rewrite | crea reserva+payment(session) → redirect `302` a `gateway.createCheckout(...).url` |
| `GET /pago/exito?session_id=...` | **nuevo** | revalida sesión en Stripe; `PENDING → HELD`; PRG a `/confirmation` (la vista de confirmación y el modelo `monto/payout` que calcula hoy `ReservationController.confirmation` NO cambian) |
| `POST /api/stripe/webhooks` | **nuevo** | endpoint para Stripe |

`CheckoutRequest` (tarjeta simulada) se **elimina**. El POST mantiene sanitización de `attendees` (1..20) e idempotencia del doble submit: si existe reserva PENDIENTE + payment PENDING con `stripeSessionId` → reusar la misma sesión (redirigir de nuevo a su URL, no crear otra).

Webhooks: Stripe no tiene CSRF ni sesión → `SecurityConfig` debe `permitAll()` + CSRF-ignoring para `/api/stripe/webhooks` (el resto sigue `anyRequest().authenticated()`).

## 7. Webhook handler

- Leer body crudo (`@RequestBody String payload`).
- `Webhook.constructEvent(payload, sigHeader, webhookSecret)` — firma inválida → **400**.
- Idempotencia por `event_id`: si el evento ya se procesó, responder 200 sin duplicar efecto. Implementación definitiva: entidad `StripeEvent` (`event_id` PK, `type`, `processedAt`) sobre tabla `stripe_event` — la crea la migración `V1__baseline.sql`;
- Eventos implementados: `checkout.session.completed` (→ HELD), `checkout.session.expired` (→ FAILED + reserva CANCELADO), `payment_intent.payment_failed` (→ HELD|PENDING → FAILED). El resto de eventos → 200 sin efecto.
- El job `EscrowReleaseService` existente sigue liberando solo HELD con ventana vencida (ahora con capture real).

## 8. Config y secretos

- Dependencia: `com.stripe:stripe-java:34.0.0` (latest estable).
- `application.properties` (los secrets **solo** env vars; el proyecto ya importa `optional:file:.env`):
  ```properties
  stripe.secret-key=${STRIPE_SECRET_KEY:}
  stripe.publishable-key=${STRIPE_PUBLISHABLE_KEY:}
  stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}
  ```
- `SK`/webhook secret jamás en el front ni en logs. Publishable key solo llega al front si algún día se usa Elements (fuera de alcance).
- Sin keys configuradas: `StripePaymentGateway` no se activa y el POST falla con mensaje claro ("pago no disponible: Stripe no está configurado") — nunca NullPointerException.

## 9. Cuenta Stripe del usuario

El usuario **no tiene cuenta aún**: implementar con env vars y guiar para crear la cuenta y obtener test keys (`sk_test_…`, `pk_test_…`) + secret de webhook. Sin keys, todo el flujo se valida con `FakePaymentGateway` en tests; el end-to-end real queda pendiente de ese paso.

## 10. Pruebas

- **Dominio/port**: `PaymentStatus`/`Payment` transiciones PENDING→HELD, FAILED; `FakePaymentGateway` simula todos los caminos (paid, expired, decline, capture, cancel, partial).
- **Checkout MVC**: `POST /checkout/{id}` crea reserva + PENDING y redirige a la URL de la sesión (fake); reintento reusa la sesión; `GET /pago/exito` revalida y va a HELD; otro usuario no puede completar el pago de la reserva ajena.
- **Webhook MockMvc**: firma válida → HELD; inválida → 400; evento duplicado → 200 sin doble efecto; `session.expired` → reserva CANCELADO.
- **Adaptar existentes**: `ReservationServiceTest` (hoy espera HELD inmediato), `ReservationFlowTests`, `E2eSmokeTests`, `CancellationFlowTests` — el estado tras checkout cambia de HELD a PENDING, y las transiciones a HELD/REFUNDED ahora pasan por el gateway fake.
- **Escrow/cancelaciones**: `EscrowServiceTest`, `EscrowReleaseServiceTest`, `ScheduledJobsTest` — `CancellationService.reembolsar` ahora cancela o capture-parcial vía gateway fake; la aserción 100%/50% del monto (`refund`) sigue siendo lo que se valida.

## 11. Constraints globales

1. El monto y la moneda se calculan SIEMPRE en el back desde `experience.price × attendees` y `experience.currencyCode` (default MXN). El front nunca manda `amount`.
2. `POST /checkout` idempotente ante doble submit (sin duplicar reserva ni pago).
3. Idempotencia de webhook por `event_id`; llamadas de captura con Idempotency-Key de Stripe.
4. Ningún PAN/CVC/SK/webhook-secret en logs ni en el front.
5. Sin keys de Stripe, el sistema debe compilar y los tests unitarios pasan (todas con fake).
6. La API pública de la web (Thymeleaf) y las notificaciones existentes no cambian de shape; `/confirmation` es PRG como hoy.
7. La UX del checkout conserva: sanitización de cupos 1..20, mensaje de error por `addFlashAttribute`, redirección final a `/confirmation`.
