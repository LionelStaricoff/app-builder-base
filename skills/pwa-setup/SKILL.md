---
name: pwa-setup
description: >
  Crea y verifica configuraciones PWA instalables (manifest, service worker,
  íconos, Content-Type). Trigger: "pwa", "manifest", "service worker",
  "instalar la app", "no se puede instalar", "agregar a pantalla de inicio".
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

- Crear un manifest + service worker desde cero
- El navegador no ofrece "instalar la app"
- Lighthouse PWA marca "not installable"
- Revisar íconos, scope o Content-Type del manifest

## Critical Patterns

> Fuente: bug real donde 3 causas independientes bloqueaban la instalación.
> Cada una por sí sola ya rompe el prompt de instalación. Verificar las 3, SIEMPRE.

### 1. Íconos con dimensiones REALES (causa más común)

Chrome descarga cada ícono y compara sus píxeles reales contra `sizes`.
Un placeholder de 1×1 declarado como `192x192` = instalación rechazada.

- Exigir `192x192` + `512x512` con `purpose: "any"`, más un `512` `maskable` (Android recorta ~20% en bordes: dejar safe zone).
- **Verificar el header IHDR del PNG, nunca el peso del archivo.** Un PNG de 70 bytes es un 1×1.
- Ver Commands para el chequeo en una línea.

### 2. Service worker en la RAÍZ (scope)

El scope de un SW es el directorio donde vive. En `/js/sw.js` solo
controla `/js/*` y jamás podrá controlar `/feed` ni `/`.

- El archivo DEBE servirse como `/sw.js` (raíz) con scope `/`.
- El SW debe tener un handler de `fetch` (Chrome lo exige para instalabilidad).
- Registrar con error visible: un `.catch(() => {})` tragó un 404 durante semanas.
  Mínimo `console.warn`.

### 3. Manifest completo + `start_url` público

Campos mínimos instalables: `name` (o `short_name`), `id`, `scope`,
`start_url`, `display: "standalone"`, `icons`, `theme_color`.

- `start_url` debe responder 200 SIN sesión. Con form-login usar `"/"`
  (la app redirige a `/login` y tras loguear cae al destino). Un `start_url`
  protegido que devuelve 302 ensucia el lanzamiento en frío.

### 4. Content-Type `application/manifest+json` determinista

El serving de estáticos resuelve el MIME por mapeos del contenedor y cae a
`application/octet-stream` para `.webmanifest`. En Spring además está
verificado que `ResourceHttpRequestHandler` ignora los `MimeMappings` del
contenedor Y los `media-types` de content-negotiation: ambos "fixes" son
código muerto.

- Fix que funciona en tests y runtime: endpoint explícito con
  `produces = "application/manifest+json"` (ver assets).
- El mapping explícito tiene precedencia sobre el resource handler de `/**`.
- Principio generalizable a cualquier stack: no confiar en MIME por
  extensión, servirlo explícito y **asegurarlo con un test**.

### 5. Checklist de verificación (en este orden)

1. Test: `GET /manifest.webmanifest` → 200 + `application/manifest+json`.
2. Test: `GET /sw.js` → 200 (público, sin login).
3. DevTools → Application → Manifest: todo verde. Service Workers: activo, scope `/`.
4. Lighthouse → categoría PWA: "Installable".
5. Instalación real solo en **HTTPS o localhost** (requisito de la plataforma, no bug).

## Code Examples

Manifest mínimo (completo en assets/):

```json
{
  "name": "MiApp",
  "short_name": "MiApp",
  "id": "/",
  "scope": "/",
  "start_url": "/",
  "display": "standalone",
  "theme_color": "#E91E63",
  "background_color": "#FFFFFF",
  "icons": [
    { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
  ]
}
```

Registro del SW sin tragar errores:

```js
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((e) => console.warn('SW:', e));
  });
}
```

## Commands

```bash
# Dimensiones reales de los PNG (header IHDR, no peso)
python -c "import struct;[(lambda d: print(f'{p}: {struct.unpack(\">II\", d[16:24])[0]}x{struct.unpack(\">II\", d[16:24])[1]}'))(open(p,'rb').read()) for p in ['icon-192.png','icon-512.png']]"
# En PowerShell (sin python):
# $b=[IO.File]::ReadAllBytes('icon-192.png'); ($b[16]*16777216)+($b[17]*65536)+($b[18]*256)+$b[19]
```

## Resources

- **Templates**: See [assets/](assets/) for `manifest.webmanifest`, `sw.js`,
  `sw-register.js` y `PwaController.java` (Spring Boot: Content-Type explícito)
