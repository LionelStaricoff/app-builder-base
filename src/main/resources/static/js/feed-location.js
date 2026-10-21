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
  const savedCountry = banner.dataset.country || '';
  const savedLat = banner.dataset.lat || '';
  const savedLng = banner.dataset.lng || '';
  // Ubicación guardada = hay coords o ciudad (aunque no se haya resuelto la
  // ciudad: el GPS puede no traer ciudad y igual estar conectado). Alimenta el
  // check verde, el texto del banner y el label del botón — no su color.
  const hasLocation = banner.dataset.hasLocation === 'true';

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text;
  }

  /**
   * El botón es SIEMPRE la acción manual (rojo, sin is-on): pide GPS otra vez
   * para cambiar de lugar. El estado "ubicación guardada" lo comunican el check
   * verde y el texto del banner — no el botón, para que nunca mienta.
   */
  function showAllowButton() {
    if (!allowBtn) return;
    allowBtn.hidden = false;
    allowBtn.classList.remove('is-on');
    const label = document.getElementById('location-allow-label');
    if (label) label.textContent = hasLocation ? 'Actualizar ubicación' : 'Activar';
  }


  /** "Cartagena de Indias, Colombia (10.3976, -75.4649)" con lo que haya. */
  function locationLabel() {
    const place = [savedCity, savedCountry].filter(Boolean).join(', ');
    const coords = (savedLat && savedLng)
      ? Number(savedLat).toFixed(4) + ', ' + Number(savedLng).toFixed(4)
      : '';
    if (place && coords) return place //+ ' (' + coords + ')';
    if (place) return place;
    if (coords) return coords + ' (sin ciudad identificada)';
    return '';
  }

  function bannerForManualSearch() {
    console.log('[location] init | hasLocation=' + hasLocation +
      ' | city="' + savedCity + '" | country="' + savedCountry + '"' +
      ' | coords=' + savedLat + ',' + savedLng +
      ' | allowBtn=' + !!allowBtn + ' | csrf=' + !!csrfToken + '/' + !!csrfHeader);
    banner.hidden = false;

    const q = new URLSearchParams(window.location.search).get('city');
    const viendoOtraCiudad = !!(q && q.toLowerCase() !== savedCity.toLowerCase());

    // Viendo otra ciudad: mostramos la ubicación guardada y dejamos el botón
    // a mano para volver/cambiar (apretarlo recarga /feed sin la búsqueda).
    if (viendoOtraCiudad) {
      setStatus(hasLocation
        ? `Viendo resultados de "${q}". Tu ubicación guardada es "${locationLabel()}".`
        : `Viendo resultados de "${q}". Activá tu ubicación para ver primero lo que hay cerca tuyo.`);
      showAllowButton();
      return;
    }

    // Sin ubicación guardada: ofrecer activarla.
    if (!hasLocation) {
      setStatus('Activá tu ubicación para ver primero experiencias cerca tuyo.');
      showAllowButton();
      return;
    }

    // Con ubicación guardada: el check verde + el texto dicen el estado;
    // el botón queda rojo y accionable para cambiar de lugar cuando quieras.
    setStatus('Ubicación : ' + locationLabel() + '.');
    showAllowButton();
  }

  async function saveLocation(lat, lng) {
    setStatus('Guardando tu ubicación…');
    console.log('[location] POST /location body={lat:' + lat + ', lng:' + lng + '}');
    try {
      const res = await fetch('/location', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader || 'X-CSRF-TOKEN']: csrfToken || '',
        },
        body: JSON.stringify({ lat, lng }),
      });
      const contentType = res.headers.get('content-type') || '';
      console.log('[location] respuesta HTTP ' + res.status + ' | content-type=' + contentType);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      // Sesión vencida: el servidor redirige al login y vuelve HTML, no JSON.
      if (!contentType.includes('application/json')) {
        throw new Error('sesion-vencida');
      }
      const data = await res.json();
      console.log('[location] guardado OK city="' + data.city + '" country="' + data.country +
        '" coords=' + data.lat + ',' + data.lng + ' -> recargando /feed');
      // Recargar sin query de búsqueda manual: el feed ya ordena por su ciudad.
      window.location.href = '/feed';
    } catch (e) {
      var mensajeError = e?.message;
      console.error('[location] fallo al guardar:', mensajeError);
      setStatus(mensajeError === 'sesion-vencida'
        ? 'Tu sesión venció. Recargá la página e intentá de nuevo.'
        : 'No pudimos guardar tu ubicación. Intentá de nuevo.');
      if (allowBtn) allowBtn.disabled = false;
    }
  }

  if (allowBtn) {
    allowBtn.addEventListener('click', () => {
      console.log('[location] click en el botón de ubicación | hasLocation=' + hasLocation +
        ' | disabled=' + allowBtn.disabled +
        ' | secureContext=' + window.isSecureContext +
        ' | protocolo=' + window.location.protocol +
        ' | host=' + window.location.host +
        ' | geolocationOK=' + !!navigator.geolocation);
      if (!navigator.geolocation) {
        setStatus('Tu navegador no soporta geolocalización. Usá la búsqueda manual.');
        return;
      }
      allowBtn.disabled = true;
      setStatus('Pidiendo tu ubicación…');
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          console.log('[location] GPS OK lat=' + pos.coords.latitude + ' lng=' + pos.coords.longitude +
            ' precision=' + pos.coords.accuracy + 'm');
          // La función ya maneja sus propios errores (try/catch adentro): se
          // marca como ignorada a propósito para no dejar una promesa suelta.
          void saveLocation(pos.coords.latitude, pos.coords.longitude);
        },
        (err) => {
          console.warn('[location] GPS ERROR code=' + err.code + ' msg=' + err.message +
            ' | secureContext=' + window.isSecureContext +
            ' | protocolo=' + window.location.protocol);
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
