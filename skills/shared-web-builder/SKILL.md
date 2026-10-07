---
name: shared-web-builder
description: Guía y genera el código para el desarrollo, maquetación e iteración de la aplicación web interactiva de Shared, basándose en su lógica de negocio y mockups visuales.
---

# Shared Web Builder

Este skill proporciona la estructura, componentes y lógica necesaria para maquetar y desarrollar la interfaz web interactiva de la plataforma **Shared**, asegurando coherencia visual con los mockups de la aplicación móvil y cumplimiento de sus reglas de dominio.

## Cuándo Usar Este Skill

Usar este skill cuando el usuario solicite:
- Crear o maquetar componentes, prototipos o módulos web interactivos para la app **Shared**.
- Implementar interfaces específicas como el Feed de experiencias, Mis Favoritos, Crear Experiencia, Notificaciones, Mensajes (Chat grupal) o Configuración.
- Desarrollar la lógica de interacción frontend (flujo de reserva, pasarela simulada de pago, confirmaciones y modales).

---

## 1. Arquitectura de Navegación y Secciones

La aplicación se estructura sobre un menú principal de 5 secciones con los siguientes componentes clave:

### 1. Feed de Experiencias (Pantalla Default)
- **Organización:** Filtrado por ubicación GPS (primero la ciudad actual del usuario, seguido por ciudades próximas).
- **Tarjeta de Experiencia (`ExperienceCard`):**
  - Avatar y nombre del organizador.
  - Imagen principal de la experiencia con botón flotante en forma de corazón (agregar/quitar favorito).
  - Título, promedio de calificación (estrellas/rating) y precio por persona.
  - Botón de acción **"Reservar"**.
- **Página de Detalle (`ExperienceDetail`):**
  - Galería de imágenes, descripción completa, duración, reglas de cupo y mapa del punto de reunión.
  - Botón fijo de reserva al final del scroll.

### 2. Mis Favoritos (`FavoritesModule`)
- **Pestaña Experiencias Favoritas:** Grid/Lista de tarjetas guardadas. Al hacer clic en eliminar, invocar modal de confirmación antes de desmarcar.
- **Pestaña Experiencias Vividas:** Lista histórica de experiencias que el usuario ha reservado y completado previamente.

### 3. Tus Experiencias (`CreatorModule`)
- **Pestaña Mis Experiencias:**
  - Lista de experiencias creadas por el usuario con opción de eliminación por tarjeta.
  - Botón flotante circular (**+**) para desplegar el formulario de creación.
- **Pestaña Mis Reservaciones:**
  - Ordenadas por la fecha más próxima.
  - Detalle por reservación: imagen, título, fecha, hora y número de asistentes anotados.
  - Botón para **Cancelar Reservación** (dispara flujo de notificación y deshabilitación de la fecha en el calendario).
- **Formulario de Creación / Edición (`ExperienceForm`):**
  - Campos: Nombre, imágenes, descripción, duración, cupo mínimo, cupo máximo y precio.
  - Selector de recurrencia:
    - *Única ocasión:* Picker de fecha y hora específica.
    - *Recurrente:* Selector de días de la semana y rangos de horarios por día.
  - Componente de Mapa Interactivo para seleccionar coordenadas ($lat, lng$) del punto de encuentro.

### 4. Notificaciones y Mensajes (`CommunicationModule`)
- **Pestaña Notificaciones:**
  - *Cancelación:* Notificación de reembolso en proceso por evento cancelado.
  - *Confirmación:* Aviso de reserva confirmada por el organizador.
  - *Solicitud:* Aviso para el creador con botón directo para **Aceptar Reservación**.
- **Pestaña Mensajes / Chats:**
  - Grupos de conversación creados automáticamente por cada instancia de experiencia (`slot_id`).
  - Chat en tiempo real que integra a los asistentes confirmados y al creador.

### 5. Configuración (`ProfileModule`)
- Edición de nombre de usuario y actualización de foto de perfil.
- Vista de puntos/acumulados (Shared Points - SP).
- Acción de Cierre de Sesión.

---

## 2. Guía del Sistema de Diseño (UI/UX)

Para mantener fidelidad visual con los mockups del proyecto:

| Elemento | Especificación / Valor CSS |
| :--- | :--- |
| **Color Primario** | `#E91E63` (Pink / Magenta brillante) |
| **Color Secundario** | `#D81B60` / `#FF4081` (Coral rosa) |
| **Fondos** | `#F8F9FA` (Superficie limpia) y `#FFFFFF` para tarjetas |
| **Texto Primario** | `#212121` (Gris oscuro) |
| **Puntuaciones / Accent** | `#FFC107` (Amarillo para estrellas) |
| **Radio de Borde (Cards)**| `16px` o `1rem` con sombras suaves (`box-shadow: 0 4px 12px rgba(0,0,0,0.08)`) |
| **Pantallas de Confirmación**| Fondo degradado rosa completo con tarjeta flotante central y tipografía destacada (ej. *"COBRASTE 190 SP"* / *"PAGASTE 190 SP"*). |

---

## 3. Flujos de Lógica Interactiva Frontend

### Flujo de Reserva
1. Usuario presiona **"Reservar"** en tarjeta o detalle.
2. Validar disponibilidad:
   - Si la experiencia es recurrente o tiene múltiples fechas $\rightarrow$ Mostrar modal con picker de fechas.
   - Si tiene fecha única $\rightarrow$ Mostrar confirmación directa de fecha/hora.
3. Formulario simulado de pago (Gateway Checkout).
4. Al confirmar, cambiar estado de la reserva a `PENDIENTE` o `CONFIRMADA`.
5. Disparar evento de notificación al creador y añadir al usuario al grupo de chat correspondiente al `slot_id`.

### Flujo de Cancelación y Reembolso
1. Al cancelar un `slot` desde *Mis Reservaciones*:
   - Actualizar estado de la reserva a `CANCELADO`.
   - Marcar la fecha en el calendario como no disponible.
   - Generar notificación de reembolso para los usuarios inscritos.