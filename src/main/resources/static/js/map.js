// Mapas Leaflet: data-map="view" (solo lectura) o data-map="pick" (elegir punto).
document.querySelectorAll('[data-map]').forEach((el) => {
  const mode = el.dataset.map;
  const lat = parseFloat(el.dataset.lat || '19.4326');
  const lng = parseFloat(el.dataset.lng || '-99.1332');
  const map = L.map(el).setView([lat, lng], 13);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
  }).addTo(map);
  const marker = L.marker([lat, lng]).addTo(map);

  if (mode === 'pick') {
    const latInput = document.getElementById('meetingLat');
    const lngInput = document.getElementById('meetingLng');
    const setPoint = (la, lo) => {
      marker.setLatLng([la, lo]);
      if (latInput) latInput.value = la.toFixed(6);
      if (lngInput) lngInput.value = lo.toFixed(6);
      map.setView([la, lo]);
    };
    // Centrar en la ubicación del usuario automáticamente.
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setPoint(pos.coords.latitude, pos.coords.longitude),
        () => {} // sin permiso: queda el punto por defecto
      );
    }
    map.on('click', (e) => setPoint(e.latlng.lat, e.latlng.lng));
    // Inicializa los inputs con el punto visible.
    if (latInput && !latInput.value) latInput.value = lat;
    if (lngInput && !lngInput.value) lngInput.value = lng;
  }
});
