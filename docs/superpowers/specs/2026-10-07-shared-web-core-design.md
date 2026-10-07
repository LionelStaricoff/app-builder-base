# Shared — Diseño de la Etapa B (Núcleo + Comunicación)

**Fecha:** 2026-10-07
**Estado:** Aprobado por el usuario (diseño conversacional + adiciones confirmadas)
**Alcance:** Experiencias, Feed, Favoritos, Reservas con escrow, Notificaciones, Chat STOMP, Configuración.

---

## 1. Objetivo y alcance

Construir la aplicación web **Shared** (plataforma de experiencias) como monolito Spring Boot con vistas Thymeleaf, implementando el núcleo de negocio más comunicación:

**En alcance (Etapa B):**
- Feed de experiencias con scroll infinito, geolocalización automática y categorías.
- CRUD de experiencias (creador) con recurrencia y mapa.
- Favoritos + Experiencias Vividas (histórico).
- Reservas con checkout simulado y **escrow** (retención de pago + comisión 5%).
- Los **3 casos de cancelación** con sus reembolsos.
- Notificaciones y Chat grupal en tiempo real (STOMP) por instancia.
- Perfil/Configuración con SP (solo vista).
- Registro +18, login con Spring Security.
- PWA mobile-first.

**Fuera de alcance (iteraciones futuras):** lógica de Shared Points (SP) como moneda, valoración mutua usuario↔creador, moderator/reporte de contenido.

---

## 2. Stack y restricciones técnicas

| Aspecto | Decisión |
|---|---|
| Framework | Spring Boot **4.1.1**, Java **25** |
| Vistas | Thymeleaf (server-side), layout con fragments |
| Persistencia | JPA + **H2** (file-based); migrable a **Postgres** sin SQL específico (evitar `CLOB`, funciones H2) |
| Seguridad | **Spring Security** (form login + registro, BCrypt, sesiones HTTP, CSRF activo) |
| Tiempo real | **WebSocket STOMP** (`spring-boot-starter-websocket`) |
| Validación | DTOs = **Java `record`** + `jakarta.validation` (`@Valid`) |
| Lombok | En entidades (`@Getter/@Setter/@Builder/@NoArgsConstructor`), ya configurado en el pom |
| `@Transactional` | **Mínimo**: solo métodos de escritura multi-entidad que lo justifiquen (p.ej. liberar escrow + notificar). Nunca decorando servicios enteros |
| CSS | **Nativo**, mobile-first, custom properties. Sin frameworks |
| Mapas | **Leaflet + OpenStreetMap** (CDN), sin API key |
| Imágenes | `MultipartFile → Base64 → columna TEXT` en tabla secundaria `ExperienceImage` (LAZY; feed carga solo la tapa, resto en detalle). Sin archivos en disco |
| PWA | `manifest.webmanifest`, service worker (cachea shell), `theme-color` `#E91E63` |
| Geolocalización | `navigator.geolocation` automática (feed y centrado de mapa), fallback a ciudad manual |

Dependencias a **agregar al pom**: `spring-boot-starter-security`, `spring-boot-starter-websocket`. El resto ya está.

---

## 3. Arquitectura de paquetes

Monolito modular por feature bajo `com.openlab.mx.share`:

```
user/          perfil, registro (+18), auth
experience/    Experience, Schedule, Instance (experiencia vivida), CRUD, categorías
reservation/   Reservation, flujos de reserva y cancelación
payment/       Payment (escrow), comisión, reembolsos, liberación
favorite/      Favorite, Experiencias Vividas
notification/  Notification + jobs @Scheduled
chat/          WebSocket STOMP, ChatMessage
shared/        config (Security, WS, Async), DataSeeder, excepciones, GlobalExceptionHandler, DTOs comunes
```

Cada paquete: `controller/ service/ repository/ entity/ dto/`.
Vistas en `src/main/resources/templates/<modulo>/`, estáticos en `static/` (CSS, JS, SW, manifest).

---

## 4. Modelo de datos

```
User ─┬─< Experience (creador)
      ├─< Favorite >─ Experience ─< ExperienceImage
      ├─< Reservation >─ ExperienceInstance >─ Experience
      │        └─< Payment (escrow)
      ├─< Notification
      ├─< ChatMessage >─ ExperienceInstance
      └─< Comment >─ ExperienceInstance
ExperienceInstance ─< Rating >─ User
```

| Entidad | Campos clave y reglas |
|---|---|
| **User** | username, email, password (BCrypt), **birthDate** (valida ≥18), avatar (base64 TEXT), ciudad/lat/lng, roles (USER, CREATOR), `spPoints` (campo presente, sin lógica) |
| **Experience** | creator, nombre, descripción, duración (min), **minCapacity / maxCapacity**, precio MXN, **category** (enum), tipo recurrencia (`UNIQUE` / `WEEKLY`), punto de encuentro lat/lng, estado (`ACTIVE / CANCELLED`), rating promedio (derivado) — **sin campo de imágenes en esta tabla** |
| **ExperienceImage** | experience (FK, `FetchType.LAZY`), `data` (**base64 TEXT**), `position` (0 = imagen principal). Tabla secundaria para no inflar las consultas del feed |
| **ExperienceSchedule** | UNIQUE: fecha + hora fija. WEEKLY: 1..N filas de día-semana + rangos horarios |
| **ExperienceInstance** ⭐ | la **experiencia vivida**: fecha, hora inicio/fin, estado (`SCHEDULED → COMPLETED / CANCELLED`), cupos derivados. Se genera del schedule; **al editar la agenda del creador se regeneran solo las futuras, nunca las pasadas** |
| **Reservation** | instance, user, asistentes, estado (`PENDIENTE / CONFIRMADA / CANCELADO`), precio pagado |
| **Payment** (escrow) | reservation, monto, **comisión 5%**, estado `HELD → RELEASED / REFUNDED_FULL / REFUNDED_PARTIAL`, `heldAt`, `settledAt` |
| **Rating** | instance + user, estrellas **1–5** → promedio en Experience |
| **Comment** | instance + user + texto + fecha (comentarios de participantes, junto a su rating) |
| **Favorite** | user + experience (constraint único) |
| **Notification** | user, tipo (`CANCELACION_REEMBOLSO`, `RESERVA_CONFIRMADA`, `SOLICITUD_RESERVA`, `PAGO_LIBERADO`), mensaje, link, `read`, createdAt |
| **ChatMessage** | instance + user + texto + timestamp. El grupo "existe" por instancia: confirmados + creador |

---

## 5. Flujos de negocio

### 5.1 Registro (+18)
Form con fecha de nacimiento → validación en DTO + service: **menor de 18 → rechazo** con mensaje. Contraseña con BCrypt.

### 5.2 Feed (scroll infinito + geolocalización + categorías)
- Orden: **ciudad actual del usuario → ciudades cercanas** (permiso automático de `navigator.geolocation`; si se niega, selector manual de ciudad).
- **Scroll infinito estilo Instagram: 1 experiencia por carga.** `GET /feed?category=&page=` con **pageSize = 1** (constante configurable); JS `IntersectionObserver` pide la siguiente cuando la tarjeta está por entrar en pantalla.
- **Proyección ligera obligatoria:** el feed consulta `ExperienceFeedDTO` (record JPA/`@Query` con constructor) con **solo la imagen tapa** (`position = 0`) y los campos de tarjeta — **nunca** `SELECT` completo de la entidad ni el arreglo de imágenes. **Carga perezosa:** el resto de las imágenes (base64) se cargan **solo en `/experience/detail`** (`FetchType.LAZY` + fetch explícito ahí).
- Tarjeta: **foto, título, descripción, punto de encuentro, duración, fecha de salida, precio**, ★ promedio, ❤️ favorito, botón Reservar.
- Chips de **categorías** para filtrar.

### 5.3 Reserva + escrow
1. "Reservar" → WEEKLY: **modal con picker de instancias**; UNIQUE: confirmación directa.
2. **Checkout simulado** (tarjeta falsa) → `Payment = HELD` + `Reservation = PENDIENTE`.
3. Creador acepta (notificación con botón) → `CONFIRMADA`; usuario ingresa al **chat** de la instancia.
4. Al terminar la instancia (job) → `COMPLETED`.
5. **Liberación del escrow**: cuando **todos** los confirmados evalúan (estrellas 1–5 + comentario) → `RELEASED`; si pasan **2 días** sin evaluar → `RELEASED` igual (job).
6. Al liberar: **el creador recibe monto − 5% de comisión**; notificación "COBRASTE $X". Pantalla de confirmación con degradado rosa.

### 5.4 Cancelaciones (3 casos)

| Caso | Efectos |
|---|---|
| **1. Participante** | Evento sigue. `Reservation → CANCELADO`; **cupo +1 inmediato**; notificación al creador; **reembolso por política**: >24h anticipación = 100%, <24h = parcial o sin reembolso; **removido del chat** |
| **2. Creador (fecha)** | Instancia → `CANCELADO` e inhabilitada en calendario; **notificación masiva** a todos los inscritos; `Payment → REFUNDED_FULL` (100%); **chat archivado** |
| **3. Cupo mínimo** | Job `@Scheduled` al vencer hora límite con asistentes < `minCapacity` (**+ botón manual** del creador) → igual que Caso 2 |

Los reembolsos consumen el escrow **antes** de que pueda liberarse; pagos cancelados jamás llegan a `RELEASED`.

### 5.5 Notificaciones
Las 3 cancelaciones + confirmación de reserva + solicitud con botón **Aceptar** + aviso de pago liberado. Persistidas en tabla (pantalla en Etapa B, acordado).

### 5.6 Chat (STOMP)
Conexión WS autenticada; sala por `instanceId`; mensajes persistidos (`ChatMessage`); solo inscritos confirmados + creador; se archiva al cancelarse la instancia.

---

## 6. Vistas y navegación

Layout: nav inferior fija de **5 íconos** + header con avatar; fragments (`card-experience`, `modal`, `star-rating`).

| # | Módulo | Contenido |
|---|---|---|
| 1 | **Feed** | scroll infinito, chips de categoría, `ExperienceCard`; `experience/detail.html`: galería, descripción, duración, cupos, mapa, comentarios, botón fijo Reservar |
| 2 | **Favoritos** | pestañas: *Favoritas* (grid + modal de confirmación para quitar) / *Experiencias Vividas* (histórico completado con rating+comentario) |
| 3 | **Tus Experiencias** | *Mis Experiencias* (lista, borrado, FAB **+** → form con recurrencia, Leaflet, imágenes múltiples) / *Mis Reservaciones* (orden fecha más próxima, asistentes, Cancelar con política) |
| 4 | **Notificaciones / Mensajes** | pestaña Notificaciones / pestaña Mensajes → grupos por instancia → `chat.html` STOMP |
| 5 | **Configuración** | editar nombre, avatar base64, vista SP (solo monto), cerrar sesión |

Adicionales: `checkout.html`, pantallas de confirmación con degradado rosa (`PAGASTE / COBRASTE / TE REEMBOLSAMOS $X`), `login.html`, `register.html` (+18).

**Design tokens:** `--color-primary: #E91E63`, secundario `#D81B60`/`#FF4081`, fondo `#F8F9FA`, texto `#212121`, estrellas `#FFC107`, cards `border-radius: 16px`, `box-shadow: 0 4px 12px rgba(0,0,0,.08)`.

---

## 7. Seguridad

- BCrypt; form login/registro propios; `th:sec` en menú; logout limpia sesión.
- `/login`, `/register` públicos; resto autenticado.
- Autorización: solo dueño edita/borra/cancela (Caso 2); solo inscritos confirmados ven su chat/notificaciones; favoritos/reservas siempre del usuario en sesión (nunca `user_id` del form).
- CSRF activo (Thymeleaf lo inyecta); consola H2 solo en dev y tras Security.
- Validación doble: `jakarta.validation` en records DTO + reglas de negocio en services.

---

## 8. Manejo de errores

- `@ControllerAdvice` (`GlobalExceptionHandler`): 404/403, errores de validación → form con mensajes (`BindingResult`), cupo agotado, fecha pasada.
- Excepciones de dominio tipadas (`CupoAgotadoException`, `EscrowException`, `MenorDeEdadException`…) → flash attributes + **redirect (PRG)**.
- Jobs `@Scheduled` con try/catch + log: un fallo de job nunca rompe la app.

---

## 9. Estrategia de pruebas

**Unitarias (JUnit 5 + Mockito, repos mockeados):**
- `ReservationServiceTest`: happy-path, límite de cupos, `PENDIENTE → CONFIRMADA`.
- `PaymentServiceTest`: liberación con todos evaluando, liberación a los 2 días, comisión 5%, reembolso 100%/parcial/nulo por política 24h, reembolso 100% Casos 2 y 3.
- `CancellationServiceTest`: Caso 1 (libera cupo, notifica, remueve chat), Caso 2 (masiva, archiva chat), Caso 3 (job + manual).
- `ExperienceServiceTest`: generación de instancias UNIQUE/WEEKLY, regeneración de futuras al editar agenda, autoría.
- `UserServiceTest`: rechazo <18.
- `FavoriteServiceTest`, `NotificationServiceTest`, `RatingServiceTest`/promedio+comentarios, `FeedServiceTest` (orden por cercanía, **pageSize = 1**, categorías) + verificación de que el feed usa `ExperienceFeedDTO` con **solo la tapa** y **no** carga el arreglo completo de imágenes base64 (query ligera + LAZY).

**Interfaz web (MockMvc + Thymeleaf):**
- Por controller: status, HTML renderizado con asserts, validación con errores en vista, `@WithMockUser` para permisos (403, dueño vs ajeno).
- Flujo encadenado reserva → checkout → confirmación.
- Chat: test de cliente **STOMP real** (`@SpringBootTest`).
- `ShareApplicationTests.contextLoads` con seed.

---

## 10. Seed y datos

- `DataSeeder` (solo si DB vacía): **2 usuarios demo** (creador + participante) con credenciales fijas y avatares base64; ciudad semilla: **CDMX** (coordenadas 19.4326, -99.1332).
- El usuario carga las experiencias a mano para probar el CRUD.
- Categorías enum iniciales: **Aventura, Gastronomía, Cultural, Deportiva, Social** (ajustables).

---

## 11. Jobs programados

1. Cancelación por `minCapacity` al vencer la hora límite (Caso 3).
2. Liberación de escrow a los 2 días de `COMPLETED` si faltan evaluaciones.
3. Marcar instancias `COMPLETED` al pasar su hora de fin.

---

## 12. Orden de implementación

Cada paso compila y trae sus tests:

1. Pom (+security, +websocket), layout, CSS tokens, PWA, User + registro/login + seed → tests auth/vista.
2. Experiencias: entidades, schedule/instancias, CRUD, Leaflet, categorías → tests unitarios + MockMvc.
3. Feed (scroll infinito + geolocalización) + Favoritos + Experiencias Vividas.
4. Reservas + checkout simulado + escrow + comisión 5% + liberación (evaluación / 2 días) + comentarios.
5. Cancelaciones (3 casos) + notificaciones + jobs.
6. Chat STOMP + pantalla de mensajes.
7. Configuración/perfil + pulido PWA + pantalla de confirmación de pago.

---

## 13. Fuera de alcance confirmado

- Lógica de SP (solo campo y vista).
- Migración Postgres (solo compatibilidad, sin configurarla ahora).
- Spring Security OAuth, moderación, mapa de calor de cupos.
