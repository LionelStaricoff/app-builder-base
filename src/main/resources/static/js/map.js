// Mapas Leaflet: view/pick + recorridos con polyline numerada.
(function () {
  const FALLBACK = { lat: 19.4326, lng: -99.1332 }; // CDMX

  function fallback(el, msg) {
    el.classList.add('map-fallback');
    el.innerHTML = '';
    const p = document.createElement('p');
    p.className = 'map-fallback';
    p.textContent = msg;
    el.appendChild(p);
  }

  function num(v, dflt) {
    const n = parseFloat(v);
    return Number.isFinite(n) ? n : dflt;
  }

  function parseStops(el) {
    // data-stops puede venir como JSON en el atributo (detalle/feed).
    try {
      const raw = el.dataset.stops;
      if (!raw || raw === '[]') return [];
      const arr = JSON.parse(raw);
      if (!Array.isArray(arr)) return [];
      return arr
        .map((s) => ({
          // Soporta tanto camelCase (JSON Jackson) como minúsculas.
          lat: num(s.lat ?? s.Lat, NaN),
          lng: num(s.lng ?? s.Lng ?? s.lon, NaN),
          placeName: s.placeName ?? s.placename ?? s.name ?? '',
          city: s.city ?? '',
        }))
        .filter((s) => Number.isFinite(s.lat) && Number.isFinite(s.lng) && (s.lat !== 0 || s.lng !== 0));
    } catch (e) {
      return [];
    }
  }

  // Pines de ubicación (el mismo símbolo que el punto de encuentro),
  // con tooltip de orden + nombre. Arrastrables solo en el editor.
  function stopLabel(i, name) {
    const title = (name || 'Parada').trim() || 'Parada';
    return `${i + 1}. ${title}`;
  }

  function addStopPin(mapOrLayer, stop, i, draggable, onDragEnd, onSelect) {
    const marker = L.marker([stop.lat, stop.lng], { draggable: !!draggable });
    marker.bindTooltip(stopLabel(i, stop.placeName || stop.name), { direction: 'top', offset: [0, -28] });
    if (draggable) {
      marker.on('dragend', () => {
        const pos = marker.getLatLng();
        onDragEnd?.(pos.lat, pos.lng);
      });
      // Tocar el pin lo deja pendiente para reubicarlo con el próximo toque.
      marker.on('click', (e) => {
        L.DomEvent.stopPropagation(e);
        onSelect?.();
      });
    } else {
      marker.bindPopup(`<strong>${escapeHtml(stopLabel(i, stop.placeName || stop.name))}</strong>`);
    }
    marker.addTo(mapOrLayer);
    return marker;
  }

  function drawRoute(map, stops) {
    const latlngs = stops.map((s) => [s.lat, s.lng]);
    stops.forEach((s, i) => addStopPin(map, s, i, false));
    if (latlngs.length >= 2) {
      L.polyline(latlngs, { weight: 4, opacity: 0.85 }).addTo(map);
      map.fitBounds(L.latLngBounds(latlngs).pad(0.25));
    } else if (latlngs.length === 1) {
      map.setView(latlngs[0], 14);
    }
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
  }

  function initRouteEdit(el, map) {
    // Editor del form (mapa de recorrido solo): click ubica la parada pendiente.
    const refresh = () => {
      map.eachLayer((layer) => {
        if (layer instanceof L.Marker || layer instanceof L.Polyline) map.removeLayer(layer);
      });
      const pts = window.__stopsApi?.located() || [];
      if (pts.length === 0) return;
      pts.forEach((p, i) => addStopPin(map, p, i, true,
        (la, lo) => window.__stopsApi?.moveTo(p.index, la, lo),
        () => window.__stopsApi?.select(p.index)));
      if (pts.length >= 2) {
        L.polyline(pts.map((p) => [p.lat, p.lng]), { weight: 4, opacity: 0.85 }).addTo(map);
        map.fitBounds(L.latLngBounds(pts.map((p) => [p.lat, p.lng])).pad(0.25));
      } else {
        map.setView([pts[0].lat, pts[0].lng], 14);
      }
    };
    map.on('click', (e) => {
      window.__stopsApi?.placeAt(e.latlng.lat, e.latlng.lng);
    });
    window.addEventListener('stops-changed', refresh);
    // Prefill inicial (edición): espera a que experience-form.js cargue las filas.
    setTimeout(refresh, 400);
    setTimeout(refresh, 1200);
  }

  function hasPendingStop() {
    // Hay fila marcada pendiente o con coords en 0 (ver experience-form.js).
    if (document.querySelector('.stop-pending')) return true;
    const latInputs = document.querySelectorAll('#stops-list input[name="stopLat"]');
    for (const input of latInputs) {
      const lngInput = input.parentElement?.querySelector('input[name="stopLng"]')
        || input.closest('[data-stop-row]')?.querySelector('input[name="stopLng"]');
      const la = parseFloat(input.value || '0') || 0;
      const lo = parseFloat(lngInput?.value || '0') || 0;
      if (!(la && lo)) return true;
    }
    return false;
  }

  function initPickRoute(el, map) {
    // Mapa único del form: punto de encuentro + paradas con polyline.
    const latInput = document.getElementById('meetingLat');
    const lngInput = document.getElementById('meetingLng');
    const meetingMarker = L.marker(map.getCenter(), { draggable: true }).addTo(map);
    meetingMarker.bindTooltip('Punto de partida (arrastrame)', { direction: 'top', offset: [0, -28] });
    const routeLayer = L.layerGroup().addTo(map);

    const setMeeting = (la, lo, moveView) => {
      if (!Number.isFinite(la) || !Number.isFinite(lo)) return;
      meetingMarker.setLatLng([la, lo]);
      if (latInput) latInput.value = la.toFixed(6);
      if (lngInput) lngInput.value = lo.toFixed(6);
      if (moveView) map.setView([la, lo]);
    };
    // Arrastrar el pin de partida lo modifica directo, sin tocar las paradas.
    meetingMarker.on('dragend', () => {
      const pos = meetingMarker.getLatLng();
      setMeeting(pos.lat, pos.lng, false);
      refreshRoute(false);
    });

    const refreshRoute = (fitBounds) => {
      routeLayer.clearLayers();
      const pts = window.__stopsApi?.located() || [];
      pts.forEach((p, i) => addStopPin(routeLayer, p, i, true,
        (la, lo) => window.__stopsApi?.moveTo(p.index, la, lo),
        () => window.__stopsApi?.select(p.index)));
      // La línea arranca en el punto de partida y sigue por las paradas.
      const start = meetingMarker.getLatLng();
      const line = [[start.lat, start.lng], ...pts.map((p) => [p.lat, p.lng])];
      if (pts.length >= 1) {
        L.polyline(line, { weight: 4, opacity: 0.85 }).addTo(routeLayer);
        if (fitBounds) map.fitBounds(L.latLngBounds(line).pad(0.25));
      }
    };

    // Estado inicial: en form nuevo el GPS pisa el default (CDMX);
    // en edición se respeta el punto guardado.
    const isFresh = el.dataset.fresh === 'true';
    const initLat = latInput?.value ? parseFloat(latInput.value) : NaN;
    const initLng = lngInput?.value ? parseFloat(lngInput.value) : NaN;
    const hasSaved = Number.isFinite(initLat) && Number.isFinite(initLng) && (initLat || initLng);
    if (hasSaved) setMeeting(initLat, initLng, true);
    if (isFresh && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setMeeting(pos.coords.latitude, pos.coords.longitude, true),
        () => {} // sin permiso: queda el default
      );
    }

    map.on('click', (e) => {
      // Si hay parada pendiente/sin ubicar, el click la ubica; si no, mueve el encuentro.
      if (window.__stopsApi && hasPendingStop()) {
        window.__stopsApi.placeAt(e.latlng.lat, e.latlng.lng);
      } else {
        setMeeting(e.latlng.lat, e.latlng.lng, false);
      }
    });
    window.addEventListener('stops-changed', () => refreshRoute(false));
    setTimeout(() => refreshRoute(true), 400);
    setTimeout(() => refreshRoute(true), 1200);
  }

  document.querySelectorAll('[data-map]').forEach((el) => initMapEl(el));
  // Tarjetas agregadas por scroll infinito: inicializar sus mini-mapas.
  window.addEventListener('feed-cards-added', (e) => {
    const root = e.detail?.list || document;
    root.querySelectorAll('[data-map]:not([data-map-ready])').forEach((el) => initMapEl(el));
  });

  function initMapEl(el) {
    if (el.dataset.mapReady === '1') return;
    el.dataset.mapReady = '1';
    if (typeof L === 'undefined') {
      fallback(el, 'Mapa no disponible (sin conexión al CDN de Leaflet).');
      return;
    }
    const mode = el.dataset.map;

    if (mode === 'edit-route' || mode === 'view-route' || mode === 'feed-route' || mode === 'pick-route') {
      let map;
      try {
        map = L.map(el, mode === 'feed-route' ? { scrollWheelZoom: false } : {}).setView(
          [FALLBACK.lat, FALLBACK.lng], 12);
      } catch (e) {
        fallback(el, 'No se pudo mostrar el recorrido.');
        return;
      }
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap',
        maxZoom: 19,
      }).addTo(map);
      setTimeout(() => map.invalidateSize(), 200);
      if (mode === 'pick-route') {
        initPickRoute(el, map);
        return;
      }
      // En el feed el mapa es lazy: solo se inicializa bien al ser visible.
      if (mode === 'feed-route') {
        setTimeout(() => map.invalidateSize(), 600);
        if ('IntersectionObserver' in window) {
          new IntersectionObserver((entries, obs) => {
            entries.forEach((en) => {
              if (en.isIntersecting) {
                map.invalidateSize();
                obs.disconnect();
              }
            });
          }).observe(el);
        }
      }
      if (mode === 'edit-route') {
        initRouteEdit(el, map);
        return;
      }
      const stops = parseStops(el);
      if (stops.length === 0) {
        fallback(el, 'Esta experiencia aún no tiene recorrido marcado.');
        return;
      }
      drawRoute(map, stops);
      return;
    }

    const lat = num(el.dataset.lat, FALLBACK.lat);
    const lng = num(el.dataset.lng, FALLBACK.lng);

    let map;
    try {
      map = L.map(el).setView([lat, lng], 13);
    } catch (e) {
      fallback(el, 'No se pudo mostrar el mapa para estas coordenadas.');
      return;
    }
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap',
      maxZoom: 19,
    }).addTo(map);
    const marker = L.marker([lat, lng]).addTo(map);

    // Si el CSS llegó tarde, recalcular el tamaño.
    setTimeout(() => map.invalidateSize(), 200);

    if (mode === 'pick') {
      const latInput = document.getElementById('meetingLat');
      const lngInput = document.getElementById('meetingLng');
      const setPoint = (la, lo) => {
        if (!Number.isFinite(la) || !Number.isFinite(lo)) return;
        marker.setLatLng([la, lo]);
        if (latInput) latInput.value = la.toFixed(6);
        if (lngInput) lngInput.value = lo.toFixed(6);
        map.setView([la, lo]);
      };
      // Solo en modo edición tiene sentido la geolocalización.
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (pos) => setPoint(pos.coords.latitude, pos.coords.longitude),
          () => {} // sin permiso: queda el punto por defecto
        );
      }
      map.on('click', (e) => setPoint(e.latlng.lat, e.latlng.lng));
      if (latInput && !latInput.value) latInput.value = lat;
      if (lngInput && !lngInput.value) lngInput.value = lng;
    }
  }
})();
