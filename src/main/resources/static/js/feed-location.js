// Cabecera del feed: chip de ubicación + botón de GPS.
// El botón pide la posición al navegador, la guarda en el perfil
// (POST /location, con reverse geocoding a ciudad en el backend) y recarga
// el feed para que la página 0 salga ya ordenada por cercanía.
(function () {
  'use strict';

  const btnGps = document.getElementById('btn-gps');
  if (!btnGps) return; // la página no tiene cabecera de ubicación

  const gpsIcon = document.getElementById('gps-icon');
  const chip = document.getElementById('location-chip');
  const cityEl = document.getElementById('location-city');
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  const initialLabel = cityEl ? cityEl.textContent.trim() : '';

  function setLabel(text) {
    if (cityEl) cityEl.textContent = text;
  }

  /** Estado "buscando": el botón se bloquea y el ícono gira. */
  function setBusy(busy) {
    btnGps.disabled = busy;
    if (gpsIcon) gpsIcon.classList.toggle('animate-spin', busy);
    if (chip) chip.classList.toggle('opacity-70', busy);
  }

  /**
   * Guarda la ubicación en el perfil. Lanza si el server no responde o si
   * la sesión venció (responde HTML de login en vez de JSON).
   */
  async function saveLocation(lat, lng) {
    const res = await fetch('/location', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        [csrfHeader || 'X-CSRF-TOKEN']: csrfToken || '',
      },
      body: JSON.stringify({ lat, lng }),
    });
    const contentType = res.headers.get('content-type') || '';
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (!contentType.includes('application/json')) throw new Error('sesion-vencida');
    return res.json();
  }

  /** Recarga el feed conservando el filtro de categoría, sin la búsqueda manual. */
  function reloadFeed() {
    const params = new URLSearchParams(window.location.search);
    params.delete('city');
    const qs = params.toString();
    window.location.href = '/feed' + (qs ? '?' + qs : '');
  }

  btnGps.addEventListener('click', () => {
    if (!navigator.geolocation) {
      setLabel('GPS no disponible');
      return;
    }

    setBusy(true);
    setLabel('Ubicando…');

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        saveLocation(pos.coords.latitude, pos.coords.longitude)
          .then((data) => {
            setLabel(data.city || data.country || initialLabel);
            reloadFeed();
          })
          .catch((err) => {
            setBusy(false);
            setLabel(err && err.message === 'sesion-vencida' ? 'Sesión vencida' : 'No se pudo guardar');
            console.error('[location] fallo al guardar la ubicación:', err);
          });
      },
      (err) => {
        setBusy(false);
        const denied = err && err.code === err.PERMISSION_DENIED;
        setLabel(denied ? 'Permiso denegado' : 'Ubicación no disponible');
        console.warn('[location] GPS error code=' + (err && err.code) + ' msg=' + (err && err.message));
      },
      { timeout: 10000 }
    );
  });
})();
