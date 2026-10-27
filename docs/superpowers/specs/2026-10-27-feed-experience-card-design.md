# Spec - La card del feed como pantalla de decisión (fechas, preguntas y reseñas)

**Fecha:** 2026-10-27
**Rama:** `feature/feed-experience-card`
**Estado:** aprobado para implementar

## 1. Problema

La card del feed muestra la próxima fecha como texto muerto. Para ver otras
fechas, los cupos reales de cada una, quiénes van, las preguntas públicas o las
reseñas, hay que navegar al detalle. El detalle ya tiene esas piezas
(`experience/detail.html`), pero la card no: la decisión de reservar se toma a
ciegas o con un viaje extra.

Además, entre elegir fecha y pagar hay una pantalla de revisión
(`GET /checkout/{instanceId}`, "Revisá y pagá", paso 2 de 2) que se agregó en
`10f9596` y que el producto no quiere: el flujo debe ser elegir fecha → Stripe,
o `/verificacion` si la identidad no está verificada. Esa pantalla es un paso
muerto, no una confirmación necesaria.

## 2. Objetivo

1. La card del feed concentra la decisión:
   - la fecha próxima es un botón que despliega las fechas futuras;
   - al elegir una fecha se actualizan los cupos disponibles, la barra de
     ocupación, el conteo y los avatares de quienes reservaron;
   - "Preguntas" y "Reseñas" se expanden y se achican dentro de la card, y se
     cargan desde el controller la primera vez (lazy);
   - el CTA lleva al detalle con la fecha y los cupos ya elegidos.
2. El detalle sigue siendo la superficie de compra: preselecciona la fecha y los
   cupos que vienen de la card y su botón "Reservar" va directo a Stripe.
3. Desaparece la pantalla intermedia: se elimina `GET /checkout/{id}` y
   `checkout.html`. `POST /reservations/{id}` crea la reserva PENDIENTE, el
   `Payment` PENDING y la Checkout Session, y redirige a la URL de Stripe. Si el
   KYC no está verificado, el `GlobalExceptionHandler` redirige a
   `/verificacion?returnUrl=...`. Si Stripe no está configurado, se vuelve al
   detalle con un error visible.

## 3. Fuera de alcance

- Escrow, webhooks, captura, payout y confirmación post-pago: no se tocan.
- Modelo de datos: no hay migración. La capacidad sigue siendo
  `Experience.maxCapacity`; lo que cambia por fecha es lo reservado.
- No se rediseña la card ni el detalle. Solo se agregan controles y se mueve la
  compra un paso.
- El detalle conserva sus secciones server-rendered de preguntas y reseñas. La
  versión lazy es la de la card. Unificarlas es una iteración aparte.
- No se agrega pago dentro de la card: la compra sigue en el detalle (decisión
  1A del usuario).

## 4. Decisiones

**D1 - Card decide, detalle compra.** El CTA de la card es un `GET` a
`/experiences/{id}?instanceId=X&attendees=N#fechas`. No crea reservas ni
sesiones. Así la card no necesita CSRF para el pago ni conoce Stripe.

**D2 - Fechas lazy, no embebidas.** La card no trae todas las instancias en el
DTO del feed (sería un N+1 por card). Al tocar la fecha se pide
`GET /experiences/{id}/dates`, que devuelve el fragmento con las fechas futuras
`SCHEDULED`. Al elegir una, `GET /experiences/{id}/dates/{instanceId}` devuelve
el bloque de cupos/avatares de esa fecha y se reemplaza en el DOM.

**D3 - Preguntas y reseñas con `<details>` nativo + lazy.** El `<summary>` es el
botón que expande y achica, con teclado y lector de pantalla gratis. El primer
`toggle` dispara el fetch del fragmento; las siguientes aperturas no vuelven a
pedir. El formulario de preguntar se intercepta con `fetch` y se re-inyecta el
fragmento, para no salir del feed.

**D4 - El bloque de cupos se reutiliza en el render inicial.** La card pinta los
cupos de la próxima instancia con los datos que ya trae `ExperienceFeedDTO`
(`availableCapacity`, `reservedCount`, `reservedAvatars`, `maxCapacity`). El
fragmento de disponibilidad renderiza el mismo markup, así no hay salto visual
al cambiar de fecha.

**D5 - `ExperienceFeedDTO` gana `nextInstanceId`.** Es el único dato nuevo en el
DTO del feed: lo necesita el `input hidden` del CTA para que el detalle sepa qué
fecha preseleccionar sin un request extra. Todo lo demás por fecha vive en
`ExperienceDateDTO`.

**D6 - `POST /reservations/{id}` es el único punto de inicio de pago.** Absorbe
la lógica de `CheckoutController.pay`: sanitiza cupos 1..20, valida KYC, crea
reserva + payment + sesión y redirige. `CheckoutController` y
`CheckoutPayRequest` se eliminan.

## 5. Contratos

### 5.1 DTO

```java
public record ExperienceDateDTO(
        Long instanceId,
        LocalDate date,
        LocalTime startTime,
        int maxCapacity,
        int availableCapacity,
        int reservedCount,
        List<String> reservedAvatars,
        Integer refundPercent) {
}
```

### 5.2 Endpoints de fragmentos (HTML, no JSON)

| Método | Ruta | Fragmento | Modelo |
|---|---|---|---|
| GET | `/experiences/{id}/dates` | `experience/card-dates :: picker` | `experienceId`, `dates`, `selectedInstanceId` |
| GET | `/experiences/{id}/dates/{instanceId}` | `experience/card-dates :: availabilityContent` | `date` (ExperienceDateDTO) |
| GET | `/experiences/{id}/questions` | `experience/card-questions :: section` | `experienceId`, `questions`, `puedePreguntar`, `esDueno`, `creatorName`, `ok`, `error` |
| POST | `/experiences/{id}/questions/fragment` | `experience/card-questions :: section` | idem, con `ok`/`error` del intento |
| GET | `/experiences/{id}/reviews` | `experience/card-reviews :: section` | `reviews` |

Todos devuelven `text/html`. Si la sesión venció, Spring redirige a `/login`; el
JS debe detectar `res.redirected` y no inyectar el login dentro de la card (mismo
guard que `feed-infinite.js`).

### 5.3 Pago

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/reservations/{instanceId}` | 302 a `session.url()` (Stripe) |
| POST | `/reservations/{instanceId}` sin KYC | 302 a `/verificacion?returnUrl=...` (handler global) |
| POST | `/reservations/{instanceId}` sin Stripe | 302 al detalle con flash `error` |
| GET | `/checkout/{instanceId}` | 404 (eliminado) |

`StripePaymentGateway` cambia el `cancelUrl` de
`/checkout/{instanceId}?attendees=N` a
`/experiences/{experienceId}?instanceId={instanceId}&attendees={attendees}#fechas`.
Para eso `StripeCheckoutService.start` agrega `experienceId` a los metadata.

## 6. Seguridad

- La app ya exige sesión para todo (`anyRequest().authenticated()`). Los
  fragmentos no son la excepción.
- Los fragmentos usan `th:text`, nunca `th:utext`: el cuerpo de la pregunta y el
  username quedan escapados.
- El formulario de preguntar lleva CSRF: Thymeleaf + Spring Security lo inyectan
  con `th:action`. El `fetch` manda el `FormData` completo, token incluido.
- `POST /experiences/{id}/questions/fragment` valida `@Valid QuestionRequest` y
  devuelve el fragmento con el error, sin redirect.
- El `POST /reservations/{id}` sigue exigiendo KYC y cupos 1..20. No se relaja
  ninguna validación al eliminar el checkout.

## 7. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| N+1 al pedir fechas | Una query de reservas por `instanceIdIn` y agrupar en memoria; no una por fecha |
| La card tiene `overflow-hidden`: un panel absoluto se recorta | El panel de fechas va en flujo normal, debajo del grid de 3 columnas; la card crece y se achica |
| El scroll infinito inyecta cards nuevas | Delegación de eventos en `document` + atributos `data-*`; nunca listeners por card |
| Tests existentes postean a `/checkout/{id}` | Actualización mecánica a `/reservations/{id}`; `CheckoutReviewTests` se borra |
| `cancelUrl` de Stripe apunta a una ruta eliminada | Metadata `experienceId` + `cancelUrl` al detalle |
| El fragmento de preguntas trae un form con CSRF | El token se genera en el render del fragmento; el fetch lo reenvía tal cual |
| Cambiar de fecha no debe perder los cupos elegidos | El input de cupos vive fuera del bloque que se reemplaza |

## 8. Criterios de aceptación

1. En `/feed`, tocar la fecha de una card abre las fechas futuras. Elegir una
   actualiza "Lugares libres", la barra, "N reservaron" y los avatares de esa
   fecha, y el label de la fecha. El panel se cierra.
2. "Reservar ahora" navega a
   `/experiences/{id}?instanceId=X&attendees=N#fechas`. El detalle muestra esa
   fecha y esos cupos preseleccionados.
3. En el detalle, "Reservar" va directo a Stripe (fake en tests). Sin KYC va a
   `/verificacion`. Sin keys de Stripe vuelve al detalle con el error visible.
4. En `/feed`, "Preguntas" y "Reseñas" expanden dentro de la card. La primera
   apertura pide el fragmento; la segunda no vuelve a pedir; se pueden achicar.
5. Preguntar desde la card actualiza la lista sin salir del feed.
6. `GET /checkout/{id}` devuelve 404 y `checkout.html` no existe.
7. Suite Java verde y e2e Playwright verde.

## 9. Verificación

- `./mvnw test` (o el wrapper de Windows) para la suite Java.
- `cd e2e; npx playwright test` para la verificación en navegador real.
- Manual: H2 en memoria + `IDENTITY_VERIFICATION_ENABLED=false`, sembrar una
  experiencia con 3 fechas y reservas distintas, y recorrer el flujo.

## 10. Notas de implementación

- El CTA dejó de ser un `<a>` y pasó a ser un `<form method="get">` (necesita
  `attendees` variable). Por eso el título de la card quedó como link al
  detalle: es el punto de entrada "sin fecha elegida" y mantiene navegable la
  card (y el preflight de e2e, que busca un `a[href^="/experiences/"]`).
- `feed-card.js` no puede declarar dos `const` con el mismo nombre en el mismo
  scope: un `SyntaxError` ahí mata TODO el archivo y los listeners nunca se
  registran (el panel queda `hidden` y vacío, sin error en pantalla). El click
  handler quedó partido en `alternarFechas` / `elegirFecha`.
- El seed de e2e le da una segunda fecha a "E2E Cerca" (`extraDate`), así que
  los tests que asumían una sola fecha usan `.first()` sobre el contenedor
  `#fechas`.
