# Spec — Contenido estructurado de la experiencia: "Qué incluye" y "Qué llevar"

**Fecha:** 2026-10-22 · **Reescrita:** 2026-10-23 · **Estado:** lista para implementar

> Esta versión **reemplaza** la anterior del mismo archivo. La anterior estaba escrita sobre
> premisas que no resisten una lectura del código; la sección 2 las lista una por una para que
> nadie vuelva a apoyarse en ellas.

## 1. Objetivo

Que el creador pueda declarar, aparte de la descripción libre, dos bloques de contenido que hoy
no existen en el modelo:

- **Qué incluye** — lo que ya está cubierto por el precio (entrada, guía, equipo, bebidas).
- **Qué llevar** — lo que el huésped tiene que traer (calzado, protector solar, efectivo).

Se muestran en la card del feed como bloques desplegables y en el detalle público como secciones
fijas. Si el creador no los completa, no se renderiza nada (silencio, no placeholders).

## 2. Correcciones a la versión anterior

| La versión anterior afirmaba | La realidad verificada |
|---|---|
| "el prototipo ya muestra los tres bloques en su sección de tarjeta" | `features/Shared - App de experiencias locales(1).html` es un **export empaquetado** de 7.8 MB (contiene `__bundler/manifest` y fuentes de Google embebidas). Tiene **0 ocurrencias** de `incluye`, `llevar` y `recorrido`. No hay DOM legible: **no sirve como fuente de verdad de UI**. |
| "`StepLineItem` y `ScheduleEntryRequest` ya existen para las paradas" | `StepLineItem` **no existe**. La entidad es `ExperienceStop` (`experience/entity/ExperienceStop.java`) y su DTO `ExperienceStopDTO`. |
| "si la migración la hace Hibernate con `ddl-auto=update` en dev" | `spring.jpa.hibernate.ddl-auto=validate` (`application.properties:7`). El esquema lo administra **Flyway** (`spring.flyway.enabled=true`, `locations=classpath:db/migration`, `baseline-version=1`). Hibernate **no crea ni altera** columnas: sin migración, la app no arranca. |
| "No hay migración aquí todavía… ver V2/V3 como precedente" | Los precedentes son `V1`…`V5`. Esta spec introduce **`V6`**. |
| "todas las actuales las tienen en cero" | Los campos **no existen**. No hay nada en cero. |
| "sin validación de beaker del form", "la hint del prototipo… ya existe hoy en Perú", "son un (carácter chino que significa «nuevo») bloque" | Texto de otro proyecto ("beaker", "Perú", un ideograma suelto). Sin sentido en este repo. |
| "el toggle existente cierra con `ta:if`" | Era `th:if`. Y desde el 2026-10-23 el toggle es `<details>/<summary>` **nativo**, no un `th:onclick`. |
| "En `ExperienceDetailTests`" | Esa clase **no existe**. La que cubre reseñas es `ExperienceDetailReviewsTests`. `FeedAndFavoritesControllerTests` sí existe. |

## 3. Estado real del código

- **Entidad** `Experience` (`@Table(name = "experiences")`): `description` es
  `@Column(nullable = false, columnDefinition = "TEXT")` (L42-43). No hay `includes` ni `bring`.
- **DTO del feed** `ExperienceFeedDTO`: `record` de 19 componentes, construido **a mano** en
  `FeedService.toDto` (L344) — no es una proyección JPQL. Hay **3 sitios de construcción
  posicional**: `FeedService.toDto:344`, `RoutePersistenceReproTests:448`, `FavoriteServiceTest:96`.
- **`FeedService.PAGE_SIZE = 1`** (L46): el feed sirve **una card por request**.
- **La card se renderiza dos veces**: `feed.html` (L121+) y el template literal de
  `feed-infinite.js` (L85+). Cualquier cambio visual va en las dos o la mitad del feed queda distinta.
- **La card no muestra `description`**. El detalle sí, como "Sobre esta experiencia"
  (`detail.html:139-141`).
- **El form** se bindea con `ExperienceFormRequest` (record) y se persiste en
  `ExperienceService.applyFields` (L214-227).

## 4. Decisiones

**D1 — Dos campos nuevos, ninguno obligatorio.** `includes` y `bring`, `TEXT` nullable.
`description` no se toca.

**D2 — En la card van DOS bloques, no tres.** "Qué harás" **no** entra a la card: la descripción
ya vive en el detalle y la card no la muestra hoy. Meterla en un dropdown duplicaría el detalle y
engordaría una card que ya es densa. *(Cambio respecto de la versión anterior, que asumía tres
bloques porque "el prototipo" los mostraba — premisa falsa, ver §2.)*

**D3 — Cada bloque es un `<details>` independiente**, hermano de "Ver Recorrido" y después de él,
en este orden: `Ver Recorrido`, `Qué incluye`, `Qué llevar`. Mismo estilo de `summary` y chevron
que el bloque ya existente (`.feed-details`, `app.css`). Sin JS: el navegador resuelve teclado y
estado expandido, y funciona igual en las cards que inyecta el scroll infinito.

**D4 — Texto libre multilínea, una línea = un ítem.** Sin subtabla, sin reordenar ítems
individualmente, sin íconos por ítem. Si más adelante se quiere eso, es un refactor con subtabla.

**D5 — Se normaliza al escribir, no al leer.** `normalizeLines()`: CRLF→LF, `trim()` por línea,
se descartan líneas vacías, se devuelve `null` si no queda nada. Así los dos renderers reciben un
string limpio y el HTML no arrastra `\r`.

**D6 — Transporte *eager* en `ExperienceFeedDTO`. Sin endpoint lazy.** Con `PAGE_SIZE = 1` la card
viaja sola: dos `TEXT` nullable no justifican un endpoint de fragmento, un skeleton, cache en el
DOM y JS nuevo. Si algún día el feed pagina más de una card por request, se revisa.

**D7 — El detalle los muestra como secciones fijas, no como dropdowns.** Ahí el usuario ya eligió
leer: esconder el contenido detrás de un click no aporta.

**D8 — Renderizado con `white-space: pre-line`, no con `<ul>`.** El string normalizado se pinta
tal cual en un `<p class="whitespace-pre-line">`. Evita partir el texto en el template (donde
Thymeleaf y el template literal de JS lo harían distinto) y respeta saltos de línea sin escapes
ni `arraySplit`. Si se quieren viñetas reales, es un cambio de presentación posterior.

## 5. Schema

`V6__experience_content.sql`:

```sql
ALTER TABLE experiences ADD COLUMN IF NOT EXISTS includes TEXT;
ALTER TABLE experiences ADD COLUMN IF NOT EXISTS bring TEXT;
```

Ambas nullable: las experiencias existentes quedan en `NULL` y la card no las muestra (D3).
Se sigue el precedente de `V4__subrubros.sql` (`ADD COLUMN IF NOT EXISTS`).

## 6. Backend

| Archivo | Cambio |
|---|---|
| `experience/entity/Experience.java` | `@Column(columnDefinition = "TEXT") private String includes;` y `bring`, justo después de `description`. |
| `experience/dto/ExperienceFeedDTO.java` | Agregar `String includes, String bring` **al final** del record (después de `reservedAvatars`). |
| `experience/service/FeedService.java` | `toDto`: pasar `experience.getIncludes()` y `experience.getBring()` en el constructor (L344). |
| `experience/dto/ExperienceFormRequest.java` | Agregar `@Size(max = 2000) String includes` y `@Size(max = 2000) String bring` al final del record. |
| `experience/service/ExperienceService.java` | `applyFields`: `experience.setIncludes(normalizeLines(request.includes()))` y lo mismo con `bring`. Nuevo helper privado `normalizeLines`. |

**Impacto de agregar componentes al record:** los 3 constructores posicionales de §3 deben
actualizarse en el mismo commit o no compila. `repopulateForm` no necesita cambios: reconstruye
el modelo desde el request, que ya trae los campos nuevos.

`@Size(max = 2000)` es una guarda, no un límite de producto: evita que un textarea con un pegado
accidental meta megabytes en la base y en la card.

## 7. UI — card del feed (dos copias)

En `feed.html`, después del `<details>` de "Ver Recorrido" (L212-235), y en `feed-infinite.js`,
dentro de `stopsHtml`/el template de la card:

```html
<details th:if="${dto.includes != null and !#strings.isEmpty(dto.includes)}"
         class="feed-details border-b border-gray-100 pb-2">
  <summary class="w-full flex items-center justify-between py-2.5 text-[10px] font-bold text-gray-400 uppercase tracking-wider hover:text-gray-600 transition-colors cursor-pointer">
    <span class="flex items-center gap-2">
      <svg aria-hidden="true" ...>…</svg>
      Qué incluye
    </span>
    <svg aria-hidden="true" class="feed-details-chevron w-3 h-3 transition-transform" ...>…</svg>
  </summary>
  <p class="whitespace-pre-line text-[11px] text-gray-600 leading-relaxed py-2 px-1"
     th:text="${dto.includes}">Incluye</p>
</details>
```

`Qué llevar` es idéntico con `dto.bring`. En el template literal de `feed-infinite.js` el `th:if`
se reemplaza por un ternario sobre `dto.includes` y el texto por `${escapeHtml(dto.includes)}`
(la función ya existe en el archivo y se usa para las paradas).

## 8. UI — detalle

En `experience/detail.html`, después de "Sobre esta experiencia" (L139-141), dos secciones con el
mismo patrón que las existentes (`<section>` + título + contenido), cada una con `th:if` sobre su
campo y `<p class="whitespace-pre-line">`.

## 9. UI — formulario

En el bloque **"3 · Detalles"** de `experience/form.html`, dos `textarea` (`rows="4"`,
`th:field="*{includes}"` y `*{bring}"`), no `required`, con etiqueta y texto de ayuda corto
(ej. "Una por línea"). Van después de la descripción, que ya existe en ese bloque.

## 10. Fuera de alcance

- Generación automática de estos textos con IA (Spring AI **no** está en el proyecto: 0 dependencias).
- Subtabla de ítems con reordenamiento/eliminación individual e íconos por ítem (D4).
- Preguntas del huésped al creador antes de reservar: requiere tabla, endpoint y autorización de
  que solo reserven/consulten los inscriptos. Spec aparte.
- Moderación de contenido.
- Endpoint lazy de fragmento (D6).

## 11. Riesgos

- **Card duplicada**: agregar los bloques en `feed.html` y olvidar `feed-infinite.js` deja la mitad
  del feed sin contenido. Es el error más probable de esta spec.
- **Record posicional**: agregar componentes a `ExperienceFeedDTO` rompe compilación en 3 sitios;
  es un fallo ruidoso, no silencioso. Bien.
- **`ddl-auto=validate`**: si se agregan los campos a la entidad y se olvida la migración, la app
  **no arranca**. No hay red de contención de Hibernate.

## 12. Pruebas

- `ExperienceServiceTest`: `normalizeLines` — CRLF, líneas vacías, solo espacios, `null`, y que un
  texto normal quede intacto.
- `ExperienceControllerTests`: crear con `includes`/`bring` → persisten; crear sin ellos → `null`.
- Render de la card (mismo patrón que `RoutePersistenceReproTests.feedConParadasRenderizaRecorrido`,
  que ya procesa el template con un `ExperienceFeedDTO` armado a mano): con `includes` no nulo →
  aparece "Qué incluye"; con `null` → **no** aparece; y el HTML no contiene `\r`.
- Render del detalle: secciones presentes/ausentes según el campo.
- Actualizar los constructores posicionales de `RoutePersistenceReproTests` y `FavoriteServiceTest`.
