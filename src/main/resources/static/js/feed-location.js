// Ubicación en el feed: pide GPS una vez, lo guarda en el perfil
// (con reverse geocoding a ciudad en el backend) y recarga para que
// la página 0 salga ya ordenada por cercanía. Incluye búsqueda manual
// por país y/o ciudad para explorar experiencias de otro lugar.
(function () {
  const banner = document.getElementById('location-banner');
  if (!banner) return;

  const statusEl = document.getElementById('location-status');
  const allowBtn = document.getElementById('location-allow');
  const manualForm = document.getElementById('location-manual-form');
  const manualInput = document.getElementById('location-manual-input');
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  const savedCity = banner.dataset.city || '';
  const dismissedKey = 'shared.location.dismissed';

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text;
  }

  function bannerForManualSearch() {
    // Sin ubicación guardada: ofrecer activarla.
    if (!savedCity) {
      banner.hidden = false;
      setStatus('Activá tu ubicación para ver primero experiencias cerca tuyo.');
      return;
    }
    // Con ubicación guardada: mostrarla siempre (el feed ya usa la última)
    // y permitir actualizarla o buscar otra ciudad manualmente.
    const q = new URLSearchParams(window.location.search).get('city');
    if (q && q.toLowerCase() !== savedCity.toLowerCase()) {
      banner.hidden = false;
      setStatus(`Viendo resultados de "${q}". Tu ubicación guardada es "${savedCity}".`);
      if (allowBtn) allowBtn.hidden = true;
      return;
    }
    banner.hidden = false;
    setStatus(`📍 Usando tu última ubicación guardada: "${savedCity}".`);
    if (allowBtn) {
      allowBtn.hidden = false;
      allowBtn.textContent = '📍 Actualizar';
    }
  }

  async function saveLocation(lat, lng) {
    setStatus('Guardando tu ubicación…');
    try {
      const res = await fetch('/location', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader || 'X-CSRF-TOKEN']: csrfToken || '',
        },
        body: JSON.stringify({ lat, lng }),
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      // Recargar sin query de búsqueda manual: el feed ya ordena por su ciudad.
      window.location.href = '/feed';
    } catch (e) {
      setStatus('No pudimos guardar tu ubicación. Intentá de nuevo.');
      if (allowBtn) allowBtn.disabled = false;
    }
  }

  if (allowBtn) {
    allowBtn.addEventListener('click', () => {
      if (!navigator.geolocation) {
        setStatus('Tu navegador no soporta geolocalización. Usá la búsqueda manual.');
        return;
      }
      allowBtn.disabled = true;
      setStatus('Pidiendo tu ubicación…');
      navigator.geolocation.getCurrentPosition(
        (pos) => saveLocation(pos.coords.latitude, pos.coords.longitude),
        (err) => {
          setStatus(
            err.code === err.PERMISSION_DENIED
              ? 'Permiso denegado. Podés activarlo desde el candado de la barra de direcciones o buscar tu ciudad manualmente.'
              : 'No se pudo obtener tu ubicación. Probá la búsqueda manual.'
          );
          allowBtn.disabled = false;
        },
        { timeout: 10000 }
      );
    });
  }

  if (manualForm) {
    manualForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const city = (manualInput?.value || '').trim();
      if (!city) return;
      // Conservar los filtros activos (categoría/país): solo se cambia la búsqueda.
      const params = new URLSearchParams(window.location.search);
      params.set('city', city);
      window.location.href = '/feed?' + params.toString();
    });
  }

  bannerForManualSearch();
})();
