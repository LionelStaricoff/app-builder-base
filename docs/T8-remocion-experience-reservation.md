# Plan 5 / T8 — Remoción de `experience` y `reservation` (inventario + secuencia)

Estado actual (commits D4/E ya aplicados): `payment` ya no importa `Reservation` ni
`ExperienceInstance` directamente; los adapters actúan como anticorrupción. Lo que
sigue es el desmantelado final de los módulos legacy.

## 0. Inventario de acoplamiento externo (fuentes principales)

Archivos fuera de `experience/` o `reservation/` que referencian los paquetes legacy:

### Legacy `experience`
- `reservation/**` (controllers/servicios/entidades) usan `Experience`, `ExperienceInstance`, `InstanceStatus`, `ExperienceRepository`.
- `payment/service/ReservationPaymentAdapter.java` (delegate de reserve).
- `payment/service/EscrowExperienceAdapter.java` (release de escrow por experiencia).
- `payment/service/Escrow...` y tests usan mocks/adapters.
- Tests de integración: `CancellationFlowTests`, `E2eSmokeTests`, `IdentityGateFlowTests`,
  `PaymentSuccessFlowTests`, `RefundPolicyViewTests`, `ReservationFlowTests`,
  `UnconfiguredCheckoutTests`, `StripeWebhookControllerTests`, `EscrowServiceTest`,
  `StripeCheckoutServiceTest`.

### Legacy `reservation`
- `experience/**` consume `Reservation`/`ReservationService` vía `ReservationPaymentAdapter`.
- `payment/**` (Estado de pago/cobro) y tests.
- Vistas: `templates/experience/*`, `templates/reservations/my-reservations.html`,
  `templates/confirmation.html`, `templates/feed.html`.
- Flyway: V1..V9/V16 incluyen tablas de experiencias/reservas/reseñas/questions.

## 1. Dependencias internas clave a romper primero
1. `reservation.service.ReservationPaymentAdapter` (implementa puertos de payment).
2. `experience.service.EscrowExperienceAdapter` (liberación de escrow).
3. `payment.service.StripeCheckoutService` ya portado, pero adapters siguen legacy.
4. `CancellationService` y `RefundPolicy` viven en reservation.
5. Templates y JS del feed/card dependen de `ExperienceFeedDTO`.

## 2. Secuencia propuesta (cortes pequeños, verde tras cada uno)

### Corte 1 — Congelar y documentar (este commit)
- Inventario en `docs/T8-remocion-experience-reservation.md`.
- Sin código.

### Corte 2 — Portar release de escrow (payment) a mobility/port
- Reemplazar `EscrowExperienceAdapter` por un port nuevo + adapter stub/bridge a mobility.
- Tests: actualizar `EscrowServiceTest` / `EscrowReleaseServiceTest`.

### Corte 3 — Portar cancelación/refund policy a payment o mobility
- Mover/crear puertos: `CancellationPolicyPort`, `RefundTierPort`.
- Copiar pruebas existentes; adapter delega a implementación temporal.

### Corte 4 — Limpieza de controllers/views de experience/reservation
- Eliminar controllers only after templates/tests migrados o deprecados.
- Retirar rutas `/experiences`, `/reservations`, `/feed` (o dejar redirect a mobility).

### Corte 5 — Eliminación de entidades/repos/servicios legacy
- Borrar paquetes `experience` y `reservation` completos cuando ya no haya imports.

### Corte 6 — Flyway/migración de esquema
- Crear migración `V17__drop_experience_reservation_tables.sql` (o equivalente) con `DROP TABLE` defensivo.
- Validar `ddl-auto=validate`.

### Corte 7 — Suite final y graft
- Ejecutar `.\\scripts\\dev.ps1` (full suite) y `graft build`.
- Confirmar 0 imports legacy.

## 3. Criterio de "hecho"
- `grep` sin imports `com.openlab.mx.movo.experience.` / `...reservation.` fuera de los paquetes mismos.
- Suite verde (baseline 331/331 actualizado).
- Flyway valida esquema.
- Commit por corte, convencional, sin co-author.

## A1 — Endpoints REST para front paralelo (código además del inventario)

Se agregaron dos controladores JSON read-only que reutilizan los servicios
legacy sin tocar las vistas Thymeleaf ni las rutas existentes:

- `com.openlab.mx.movo.experience.controller.api.FeedApiController`
  - `GET /api/feed?page&category&country&cityQuery&lat&lng&maxDistanceKm`
  - respuesta: `Page<ExperienceFeedDTO>` (Spring Page) con todo lo que usa el feed/feed-card.
- `com.openlab.mx.movo.experience.controller.api.ExperienceApiController`
  - `GET /api/experiences/{id}`
  - respuesta: `ExperienceDetailResponse`
    (`id, name, description, includes, bring, durationMinutes, minCapacity, maxCapacity, price, currencyCode, category, ratingAverage, meetingLat, meetingLng, creatorName, images[]`).

- `com.openlab.mx.movo.notification.controller.api.NotificationApiController`
  - `GET /api/notifications` → `List<NotificationResponse>`
    (`id, type, message, link, read, createdAt`)
  - `POST /api/notifications/{id}/read` → `NotificationResponse`

- `com.openlab.mx.movo.user.controller.api.ProfileApiController`
  - `GET /api/profile` → `UserProfileResponse`
    (`id, username, email, avatarBase64, city, country, currencyCode, role, spPoints, interestsOnboarded, proposalOnboarded`)

Notas para el front:
- Autenticados: como el resto de la app, van con cookie de sesión.
- `GET` no requiere CSRF.
- Los campos coinciden con los DTOs ya usados por FeedService/ExperienceService;
  no inventar nuevos contratos en el front.
