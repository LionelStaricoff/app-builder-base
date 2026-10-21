// Scroll infinito estilo Instagram: paga la siguiente página (pageSize=1)
// cuando el sentinel está por entrar en pantalla. La ordenación por
// ubicación la resuelve el backend con la ciudad guardada del perfil.
(function () {
  const list = document.getElementById('feed-list');
  const sentinel = document.getElementById('feed-sentinel');
  const loading = document.getElementById('feed-loading');
  if (!list || !sentinel) return;

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const params = new URLSearchParams(window.location.search);
  const category = params.get('category');
  const country = params.get('country');
  const city = params.get('city');
  let page = 1;
  let loading_page = false;
  let done = false;

  function cardHtml(dto) {
    let images = Array.isArray(dto.images) ? dto.images : [];
    if (images.length === 0 && dto.coverBase64) {
      images = [dto.coverBase64];
    }
    const slides = images.map((b64) => `
      <figure class="carousel-slide">
        <a href="/experiences/${dto.id}">
          <img src="data:image/jpeg;base64,${b64}" alt="" loading="lazy">
        </a>
      </figure>`).join('');
    const gallery = images.length > 0
      ? `<div class="carousel" data-carousel>
           <div class="carousel-track" data-carousel-track>${slides}</div>
           <button type="button" class="carousel-btn prev" data-carousel-prev aria-label="Anterior">‹</button>
           <button type="button" class="carousel-btn next" data-carousel-next aria-label="Siguiente">›</button>
           <div class="carousel-dots" data-carousel-dots></div>
           <span class="carousel-counter" data-carousel-counter>1 / 1</span>
         </div>`
      : '';
    const heartAction = dto.favorite
      ? `/favorites/${dto.id}/remove`
      : `/favorites/${dto.id}/add`;
    const heart = dto.favorite ? '❤️' : '🤍';
    let creatorAvatar = '<span class="card-creator-fallback" aria-hidden="true">👤</span>';
    if (dto.creatorAvatar) {
      creatorAvatar = `<img class="card-creator-avatar" src="data:image/jpeg;base64,${escapeAttr(dto.creatorAvatar)}" alt="" loading="lazy">`;
    }
    const creator = dto.creatorName
      ? `<div class="card-creator">
           ${creatorAvatar}
           <span class="card-creator-name">${escapeHtml(dto.creatorName)}</span>
           <span class="card-creator-tag">· armó este viaje</span>
         </div>`
      : '';
    const next = dto.nextDate || 'Sin fecha';
    const currency = dto.currencyCode || 'MXN';
    const duration = formatDuration(dto.durationMinutes || 60);
    const stops = Array.isArray(dto.stops) ? dto.stops : [];
    const lastStop = stops.length > 1
      ? ' → ' + escapeHtml(stops[stops.length - 1].placeName || '')
      : '';
    const routeHtml = stops.length > 0
      ? `<div class="feed-route">
           <div class="map-box feed-route-map" data-map="feed-route" style="height:180px"
                data-stops='${escapeAttr(JSON.stringify(stops))}'></div>
           <p class="hint">🗺️ ${stops.length} paradas · ${escapeHtml(stops[0].placeName || '')}${lastStop}</p>
         </div>`
      : '';
    return `
      <div class="card" id="card-${dto.id}">
        ${gallery}
        <div style="padding:1rem">
          ${creator}
          <div class="card-head">
            <h3><a href="/experiences/${dto.id}">${escapeHtml(dto.name)}</a></h3>
            <span class="card-head-actions">
            <button type="button" class="heart share-btn" title="Compartir viaje"
                    aria-label="Compartir viaje" data-share-experience="${dto.id}">📤</button>
            <form class="heart" action="${heartAction}" method="post">
              <input type="hidden" name="_csrf" value="${csrfToken || ''}">
              <button type="submit">${heart}</button>
            </form>
            </span>
          </div>
          <p>${escapeHtml(dto.description)}</p>
          <div class="card-capacity">
            <span>Lugares libres:</span>
            <strong class="card-capacity-free">${dto.availableCapacity ?? 0}</strong>
            ${dto.reservedCount > 0 ? `
              <span class="card-capacity-reserved">· ${dto.reservedCount} personas ya reservaron
                <span class="avatar-stack">
                  ${(Array.isArray(dto.reservedAvatars) ? dto.reservedAvatars : [])
                    .map((av) => `<img src="data:image/jpeg;base64,${escapeAttr(av)}" alt="" loading="lazy">`).join('')}
                </span>
              </span>` : ''}
          </div>
          <p class="meta">📍 ${escapeHtml(dto.meetingPointLabel)} · ⏱ ${duration} · 📅 ${next} · <strong>${escapeHtml(currency)} ${dto.price}</strong></p>
          ${routeHtml}
          <p class="meta"><span class="stars">★</span> ${(dto.ratingAverage ?? 0).toFixed(1)}</p>
          <a class="btn-primary" href="/experiences/${dto.id}">Reservar</a>
        </div>
      </div>`;
  }

  function formatDuration(total) {
    total = Math.max(1, Number.parseInt(total, 10) || 60);
    const h = Math.floor(total / 60);
    const m = total % 60;
    if (h === 0) return `${m} min`;
    if (m === 0) return `${h} h`;
    return `${h} h ${m} min`;
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
  }

  function escapeAttr(text) {
    return String(text).replaceAll('&', '&amp;').replaceAll("'", '&#39;').replaceAll('<', '&lt;');
  }

  async function loadNext() {
    if (loading_page || done) return;
    loading_page = true;
    if (loading) loading.hidden = false;
    try {
      const query = new URLSearchParams({ page: String(page) });
      if (category) query.set('category', category);
      if (country) query.set('country', country);
      if (city) query.set('city', city);
      const res = await fetch(`/feed/api?${query}`, {
        headers: { Accept: 'application/json' },
      });
      const data = await res.json();
      const items = data.content || [];
      if (items.length === 0) {
        done = true;
      } else {
        items.forEach((dto) => list.insertAdjacentHTML('beforeend', cardHtml(dto)));
        window.initCarousels?.(list);
        // Inicializar mini-mapas de recorrido recién agregados.
        list.querySelectorAll('[data-map="feed-route"]:not([data-map-init])').forEach((el) => {
          el.dataset.mapInit = '1';
        });
        // map.js ya observa [data-map] al cargar; para nodos nuevos disparamos re-scan.
        window.dispatchEvent(new CustomEvent('feed-cards-added', { detail: { list } }));
        page += 1;
      }
    } catch (e) {
      // Si el listado falla no hay más páginas que pedir: se corta el scroll.
      done = true;
    } finally {
      loading_page = false;
      if (loading) loading.hidden = true;
    }
  }

  const observer = new IntersectionObserver(
    (entries) => entries.forEach((e) => e.isIntersecting && loadNext()),
    { rootMargin: '400px' }
  );
  observer.observe(sentinel);
})();
