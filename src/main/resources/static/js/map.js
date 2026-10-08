// Mapas Leaflet: data-map="view" (solo lectura) o data-map="pick" (elegir punto).
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

  document.querySelectorAll('[data-map]').forEach((el) => {
    if (typeof L === 'undefined') {
      fallback(el, 'Mapa no disponible (sin conexión al CDN de Leaflet).');
      return;
    }
    const mode = el.dataset.map;
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
  });
})();
