# Spec — Campos de contenido de la experiencia (qué harás / qué incluye / qué llevar)

**Fecha:** 2026-10-22 · **Estado:** aprobado en chat (diseño conversacional) · **Base:** `docs/superpowers/specs/2026-10-07-shared-web-core-design.md` (§2 stack, §4 schema que "se puede ampliar", §5.1 registro y §6.6 trade-offs; y §1 "fuera de alcance": valoración mutua, SP como moneda, SP perseguido)

> Campos del **formulario de creación/edición** (§6) y cómo se distribuyen después a **card del feed** (§5.2) y **detalle público** (§6.2).

## 1. Objetivo

Que una experiencia pueda contar, aparte de la descripción libre (`Experience.description`, ya existe), tres bloques estructurados que el prototipo (`features/…Shared — App de experiencias locales(1).html`) ya muestra en su sección de "tarjeta":
- **Qué harás** (esencialmente `description`, pero re-etiquetado y re-acomodado para la card);
- **Qué incluye** (lista de lo que ya se cubre en el precio);
- **Qué llevar** (lista de lo que el huésped tiene que traer).

Esto agrega **solo** esos dos campos estructurados (`includes`, `bring`) al modelo y al form; la descripción sigue siendo `description`.

## 2. Decisiones aprobadas por el usuario

1. **Los tres bloques de la card del feed entran como dropdowns dentro del área que hoy tiene "Ver Recorrido"** (el toggle existente cierra con `th:if` cuando hay paradas). No se agregan nuevas secciones fijas, ni se toca el layout ni la estética de marca de la card (colores teal de la marca, tipografía existing).
2. **"Qué harás / qué incluye / qué llevar" en la card son solo visuales**: si la experiencia no tiene los datos (todas las actuales las tienen en cero), **no se muestra nada** (recomendado del usuario: silencio, no `Lorem`/`placeholder`).
3. **Preguntas y reseñas van en el detalle, no en la card** (recomendado del usuario): el detalle ya tiene reseñas con texto (`ExperienceDetailReviewsTests`); las preguntas del creador son un新增 bloque del detalle, sin llevarlas al feed.

## 3. Decisiones técnicas (D1–D5, aprobadas en charla)

### D1 — Los campos son *alimentables* pero no obligatorios

`Experience.includes` y `Experience.bring` son `TEXT`, nullable, sin validación de beaker del form (pueden venir vacíos). La hint del prototipo del creador ("asegurate de incluir…") ya existe hoy en Perú; para Shared no tenemos esa fuente de datos de momento, así que el creador los escribe a mano — el prototipo no aporta lógica de generación aquí.

### D2 — "Qué harás" es `description`, no un campo nuevo

La descripción libre ya cubre "qué harás". Los tres dropdowns los alimenta:
- **Qué harás** ← `experience.description` (renderizado tal cual en el body del dropdown);
- **Qué incluye** ← `experience.includes` (renderizado como lista si existe y no está vacío);
- **Qué llevar** ← `experience.bring` (renderizado como lista si existe y no está vacío).

Ninguna transformación de texto, ninguna concatenación con listas; el cuerpo de cada dropdown es el string del campo, posiblemente con saltos de línea convertidos a `<br>` o `<li>` a elección de CSS.

### D3 — Las listas `includes`/`bring` son texto libre multilínea

No hay subtabla "qué incluye item" (nadie pidió gestionarlo). El creador escribe en el textarea de cada campo una lista a granel (ej. "Entrada al museo, guía bilingüe, bebidas"). El renderizado en la card los muestra tal cual (procesado por CSS para los saltos). Si en el futuro se quiere permitir reordenar/eliminar items individualmente, se haría en un refactor aparte con subtabla — fuera del alcance de esta spec.

### D4 — Silencio por `th:if` (card): mostrar los dropdowns solo si hay contenido

- El dropdown **Qué harás** aparece cuando `experience.description` no está vacío (es siempre no vacío hoy por el `required` del form; pero se mantiene `th:if` por si futuro quita required).
- Los dropdowns **Qué incluye** y **Qué llevar** aparecen cuando su campo no está vacío, con `th:if` o `th:switch` según convenga.
- El orden del prototipo (Qué harás, Qué incluye, Qué llevar) se respeta en el HTML de la card cuando los hay.

### D5 — En el detalle, preguntas + reseñas son bloques independientes con conteo en el DTO

- **Reseñas** (ya existen como `Rating`+`Comment`): el detalle las renderiza con texto real (ver `ExperienceDetailReviewsTests`). Bloque visible en detalle público.
- **Preguntas del huésped al creador antes de reservar**: nuevo bloque del detalle, sin backend hoy — se especifica aparte cuando se pida.
- El DTO del feed (`ExperienceFeedDTO`) **no** trae ni reseñas ni preguntas de cada card (solo el promedio ligero, ya existe). Hacerlo implicaría cambiar la proyección del feed, decisión aparte.

## 4. Schema

`"se puede ampliar"` (core spec §4).

| Campo | Tipo | NotNull | Observación |
|---|---|---|---|
| `includes` | TEXT | no | Lista "qué incluye". Nuevo. |
| `bring` | TEXT | no | Lista "qué llevar". Nuevo. |

`StepLineItem` y `ScheduleEntryRequest` ya existen para las parada/itinerario del recorrido — no se tocan.

No hay migración aquí todavía: se escribe y aprobada, luego plan aparte la migración + form + card. Si la migración la hace Hibernate con `ddl-auto=update` en dev (solo dev, validado en CI), se documenta; si no, ver V2/V3 como precedente.

## 5. UI — card del feed (solo distribución de los dropdowns, sin new markup de layout)

Dentro del bloque desplegable que hoy tiene **"Ver Recorrido"** (toggle existente, con `ta:if` cuando hay paradas), se agregan —manteniendo el estilo y la paleta existente— los tres dropdowns estructurados del prototipo:

- **Qué harás** (body = `description`)
- **Qué incluye** (body = `includes`, lista, solo visible si no vacío)
- **Qué llevar** (body = `bring`, lista, solo visible si no vacío)

Ninguno de estos dropdowns aparece si el campo correspondiente está vacío (T4). El "Ver Recorrido" original sigue su `th:if` de paradas (no se pierde).

Nota de implementación: si se quiere mantener la card compacta antes de mostrar contenido, la open/close del dropdown se reusa del toggle existente — no se abre todo junto; cada dropdown cierra/abra su propio toggle si se prefiere (decisión de UI en la implementación, sin cambiar especificación).

## 6. UI — detalle público (§6.2 core)

- **Reseñas con texto**: ya implementadas (Rating + Comment, `ExperienceDetailReviewsTests`). Sin cambios en esta spec.
- **Preguntas del huésped** (creador responde): nuevo bloque del detalle, sin backend todavía — se hace spec aparte cuando se pida (requiere tabla + endpoint + autorización de que solo inscritos confirmados pregunten, consistente con §6.18 del core spec).

## 7. UI — formulario de creación/edición (StepLine / form)

Se agregan al form dos **campos de texto largo** (textarea), al final del bloque de contenido, antes de Recurrencia:

- **Qué incluye** (`th:field="*{includes}"`) — textarea, no required (T1).
- **Qué llevar** (`th:field="*{bring}"`) — textarea, no required (T1).

El **Qué harás** ya existe como `description` (textarea required del form). No se agrega campo nuevo; se puede re-etiquetar o re-acomodar en el form, pero no es obligatorio para la spec — queda al gusto del creador en la implementación.

Si el creador no completa `includes`/`bring`, el form los guarda como `null`/vacío y la card los omite (T4).

## 8. Validación / límites

- Longitud: sin límite explícito hoy; si se hace, se fija en el plan/implementación (core spec §14 "mensajes de error claros y estáticos").
- No se valida contenido (sin moderación/reporte de contenido — fuera de alcance, core spec §1).

## 9. Fuera de alcance

- **Generación automática con Spring AI** de estos textos al crear la experiencia (user pidió revisarlo aparte; queda standalone: preferencias → propuestas IA → usar/descartar).
- Tabla "ítems de incluye/llevar" con reordenamiento/eliminación individual (D3).
- **Íconos** por item (solo texto por ahora).
- Integración de "hábiles / días con más reservas" que menciona el prototipo en el precio sugerido — eso es propuesta/auto-precio; fuera.
- **Preguntas como entidad persistente** — sin backend todavía (§6).

## 10. Riesgos / rollback

- **Mayor**: olvidar el `th:if` de los dos dropdowns y que "Qué incluye"/"Qué llevar" se rendericen vacíos en todas las cards actuales hasta completarse los campos — mitigable revertiendo el template; los datos históricos no se rompen.
- **Label vs campo**: si en el futuro `description` deja de cubrir "qué harás" y se hace campo separado, es un cambio menor en el modelo + migración + renombre de label; sin impacto en includes/bring.

## 11. Pruebas (TDD, asignadas de antemano)

En `ExperienceDetailTests` / `ExperienceControllerTests` (bajo `src/test/...experience/`):

- Crear experiencia con `includes` y `bring` → persisten en la entidad (SELECT)
- Crear experiencia sin `includes`/`bring` → null/vacío y la card los omite (verificado en card con `th:if`)
- Editar el form con los dos campos → se actualizan (update)

En card del feed (probablemente `FeedAndFavoritesControllerTests` o nuevo por card):

- Card con `includes`/`bring` → muestra los 3 dropdowns
- Card sin `includes`/`bring` (historicales) → no muestra esos dropdowns (silencio T4), solo "Ver Recorrido" si corresponde

La carta de pruebas concreta se resuelve en el plan de implementación, no aquí.
