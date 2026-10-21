# Recursos vendorizados

Estos archivos son de terceros y viven en el repo (en vez de cargarse desde un
CDN) por dos razones: la app no depende de un CDN en runtime y la integridad
del archivo la controla el repo en vez de un tercero.

Cada versión está fijada. Para actualizar, cambiá la URL, bajá el archivo
nuevo y actualizá esta tabla.

| Archivo | Origen | Versión | Licencia |
|---|---|---|---|
| `leaflet/leaflet.css`, `leaflet/leaflet.js` | <https://unpkg.com/leaflet@1.9.4/dist/> | 1.9.4 | BSD-2-Clause (`leaflet/LICENSE`) |
| `leaflet/images/*.png` | <https://unpkg.com/leaflet@1.9.4/dist/images/> | 1.9.4 | BSD-2-Clause (`leaflet/LICENSE`) |
| `sockjs/sockjs.min.js` | <https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/> | 1.6.1 | MIT (`sockjs/LICENSE`) |
| `stomp/stomp.min.js` | <https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/> | 2.3.3 | MIT (`stomp/LICENSE.txt`) |

## Notas

- **Leaflet**: el CSS referencia las imágenes con rutas relativas
  (`images/marker-icon.png`), por eso los PNG van en `leaflet/images/`. Si se
  cambia de lugar, hay que moverlos juntos.
- **STOMP**: se usa el paquete `stompjs` (no `@stomp/stompjs`) a propósito: el
  bundle expone el global `Stomp` con el API `Stomp.over(...)` que espera
  `static/js/chat.js`. `@stomp/stompjs` expone `StompJs` y un API distinto.
- `VendorAssetsTests` verifica que los archivos se sirvan y que las imágenes
  que pide el CSS existan.
