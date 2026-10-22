# Spec — Instant book: confirmar la reserva al pagar

**Fecha:** 2026-10-22 · **Estado:** aprobada en chat por el usuario (diseño íntegro) · **Base:** `docs/superpowers/specs/2026-10-14-stripe-checkout-design.md` (§4 escrow ↔ Stripe, §6 rutas de pago) y `docs/superpowers/specs/2026-10-07-shared-web-core-design.md` (§5.3 reservas, §5.6 chat)

## 1. Objetivo

Que **el pago confirme la reserva**: al completarse el cobro, la reserva pasa a `CONFIRMADA` y el huésped entra al chat grupal **de inmediato**, en línea con el prototipo (`features/…Shared — App de experiencias locales(1).html`, pantalla "Reservar experiencia": *"Te unimos al chat grupal"* sin esperas). Hoy la reserva queda `PENDIENTE` hasta que el creador confirma a mano desde una cola, y el chat recién se abre ahí: dos fricciones de más.

El **escrow no cambia**: `EscrowService.tryRelease` exige además `payment HELD` + instancia `COMPLETED` + (todos evaluaron o ventana de 2 días), así que confirmar al pagar **no libera dinero antes de tiempo**.

## 2. Decisiones aprobadas por el usuario

1. **Instant book total**: el pago confirma y se **elimina** la confirmación manual del creador (cola "Esperando tu confirmación" + `POST /reservations/{id}/confirm`). El creador se protege con lo que ya existe: cancelar fecha con reembolso y `minCapacityJob`.
2. **Notificar al pagar, a los dos**: `RESERVA_CONFIRMADA` al huésped (link al chat) y al creador (link a Mis reservas). Se elimina el aviso previo `SOLICITUD_RESERVA`, que se disparaba **antes** de que exista dinero.

## 3. Flujo actual vs. nuevo

| Paso | Hoy | Con instant book |
|---|---|---|
| `POST /checkout/{id}` → `StripeCheckoutService.start()` | Reserva `PENDIENTE` + `Payment PENDING` + aviso `SOLICITUD_RESERVA` al creador | Igual, **sin** el aviso al creador |
| Retorno `/pago/exito` **o** webhook `checkout.session.completed` → `complete()` | `Payment PENDING → HELD`; la reserva **sigue** `PENDIENTE` | En la misma transacción: `HELD` **y** reserva `PENDIENTE → CONFIRMADA` + las 2 notificaciones |
| Creador: cola + `POST /reservations/{id}/confirm` | `PENDIENTE → CONFIRMADA` + aviso al huésped | **Eliminado** |
| Huésped | Chat accesible recién al confirmar | Chat accesible **al instante** (`ChatAuthorizationService` exige `CONFIRMADA` ✓) |
| Webhook `checkout.session.expired` | `Payment FAILED` + reserva `CANCELADO` | **Sin cambios** (la reserva sigue `PENDIENTE` pre-pago) |
| Escrow / `minCapacityJob` / cancelaciones / rating | — | **Sin cambios** (`countAttendees` ya suma `PENDIENTE` + `CONFIRMADA`) |

Punto único de confirmación: **`StripeCheckoutService.complete()`** — lo llaman tanto `/pago/exito` como el webhook. Idempotencia intacta: si el pago ya no es `PENDING`, `complete()` retorna antes, así que **nunca** se confirma ni se notifica dos veces.

## 4. Cambios por archivo

**Producción**

| Archivo | Cambio |
|---|---|
| `payment/service/StripeCheckoutService.java` | En `complete()`, dentro de la rama `PENDING → HELD`: si la reserva es `PENDIENTE`, pasarla a `CONFIRMADA` y notificar (§6). Inyectar `ReservationRepository` (ya lo tiene) y `NotificationService` (nuevo) |
| `payment/controller/CheckoutController.java` | Eliminar el bloque que envía `SOLICITUD_RESERVA` en `pay()`; si `NotificationService` queda sin uso en la clase, eliminar también el campo y su import |
| `reservation/controller/ReservationController.java` | Eliminar `POST /reservations/{id}/confirm` (el resto de rutas — `GET /confirmation` y el endpoint `.ics` — no cambia). Sacar `model.addAttribute("pendientes", …)` de `myReservations()` y `nombreCreador` de `confirmation()` (el hint pasa a ser sobre el pago). Limpiar lo que quede sin uso: `NotificationService`, `ReservationStatus` y `PreAuthorize` |
| `reservation/service/ReservationService.java` | Eliminar `confirm(Long, User)` |
| `templates/reservations/my-reservations.html` | Eliminar la sección "Esperando tu confirmación" |
| `templates/confirmation.html` | El hint de reserva `PENDIENTE` cambia de *"cuando {creador} confirme"* (mentira post-cambio) a *"El chat grupal se abre cuando se complete el pago."* |

**Datos**

- `db/migration/V3__instant_book_backfill.sql` (nuevo): las filas históricas `PENDIENTE` **con pago ya `HELD`/`RELEASED`** nunca se confirmarían solas:

```sql
UPDATE reservations
   SET status = 'CONFIRMADA'
 WHERE status = 'PENDIENTE'
   AND EXISTS (SELECT 1 FROM payments
                WHERE payments.reservation_id = reservations.id
                  AND payments.status IN ('HELD', 'RELEASED'));
```

Los enums se persisten como texto (`@Enumerated(EnumType.STRING)` en `Reservation` y `Payment`), así que la comparación con `'CONFIRMADA'`/`'HELD'` es directa. Sentencia portable (Postgres + H2 de tests) e idempotente. Las `PENDIENTE` con pago `PENDING`/`FAILED` siguen su ciclo normal (el `expired` las cancela).

## 5. Estados que quedan

- **`PENDIENTE`** = reserva creada al entrar al checkout, **sin pagar aún** (reintento, refresh, sesión abandonada). Sigue existiendo y `reserve()` no cambia.
- **`CONFIRMADA`** = pago completado. La consigue todo el mundo al pagar; ya no hay transición manual.
- **`CANCELADO`** = sin cambios (webhook `expired`, cancelación de fecha, cupo mínimo).

## 6. Notificaciones

En `complete()`, solo cuando ocurre la transición `PENDIENTE → CONFIRMADA`:

| Destinatario | Tipo | Mensaje (orientativo) | Link |
|---|---|---|---|
| Huésped | `RESERVA_CONFIRMADA` | "Tu reserva en {experiencia} está confirmada — entrá al chat grupal" | `/chats/{instanceId}` |
| Creador | `RESERVA_CONFIRMADA` | "{usuario} reservó {N} lugar(es) en {experiencia}" | `/reservations` |

- Se elimina el envío de `SOLICITUD_RESERVA` en `CheckoutController.pay()`.
- El enum **conserva** `SOLICITUD_RESERVA` (filas históricas; sin migración de datos).

## 7. Pruebas (TDD)

**Nuevas** — asignadas de antemano para que el plan no tenga que decidirlo:

En `PaymentSuccessFlowTests` (ya maneja el gateway fake y maneja `/pago/exito`):

1. Tras `/pago/exito`: la reserva pasa a `CONFIRMADA` y `GET /chats/{instanceId}` responde 200 para el huésped.
2. Se envían las 2 notificaciones (huésped con link al chat, creador con link a `/reservations`).
3. Segunda llamada a `complete()` (idempotente): no re-confirma ni duplica avisos.

En `ReservationFlowTests`:

4. `POST /reservations/{id}/confirm` ya **no existe** (404) y `GET /reservations` no renderiza la cola "Esperando tu confirmación".

**Ajustes inevitables por el cambio** (6 archivos):

| Test | Qué toca |
|---|---|
| `PaymentSuccessFlowTests` | Su helper `pago()` busca la reserva **con filtro `PENDIENTE`** y se usa también *después* de `/pago/exito` → dejar de filtrar por estado |
| `E2eSmokeTests` | Paso `POST /reservations/{id}/confirm` eliminado → quitarlo y assertear `CONFIRMADA` tras el pago |
| `CancellationFlowTests` | Idem (sembrar `CONFIRMADA` directo en repositorio) |
| `ChatStompTests` | Llamaba `reservationService.confirm(...)` → sembrado directo |
| `ReservationServiceTest` | Casos unitarios de `confirm()` → eliminarlos (con el método) |
| `PostPagoConfirmationTests` | Copy del hint de `PENDIENTE` y `nombreCreador` (ya no se usa) |

`StripeCheckoutServiceTest` y `StripeWebhookControllerTests` pueden necesitar stubs extra por la inyección de `NotificationService` — se revisan al correr la suite.

## 8. Riesgos y rollback

- **Mayor:** tests de flujo que afirman estados intermedios (lista arriba). Mitigación: TDD + suite completa (`mvnw test`, hoy 301 en verde).
- **Migración `V3`:** es un `UPDATE` acotado e idempotente; corre también en los tests H2 (sin filas al arrancar = no-op). Rollback = migración `V4` con la condición inversa si hiciera falta (no se reescribe V3 ya aplicada).
- **Deshacer el feature:** revert de los commits correspondientes; `PENDIENTE→CONFIRMADA` manual volvería a habilitarse al restaurar `confirm()`.

## 9. Fuera de alcance

- Rechazo o expulsión de reservas por el creador (descartado por decisión del usuario).
- Instant book **por experiencia** (flag en `Experience`).
- El copy preexistente de `/confirmation` que dice "PAGASTE" con una reserva sin pagar (existe hoy; aparte).
- Recordatorio "1 hora antes" del prototipo.
- Email/push: solo notificaciones in-app (las únicas que existen).

## 10. Constraints globales

- **Marca visual intacta**: los cambios de UI usan clases y variables existentes (`.hint`, `.btn-*`, `--color-*`, los 5 temas `data-style`). El prototipo aporta solo funcionalidad.
- Flyway administra el esquema; `ddl-auto=validate`; sin SQL específico de motor.
- Mensajes de commit en castellano, Conventional Commits con scope de clase.
- Todo se acumula en la rama `feature/v2` (commits por tarea terminada).
