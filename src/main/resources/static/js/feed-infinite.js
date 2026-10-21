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
           <div class="carousel-pagination">
             <button type="button" data-carousel-prev>‹</button>
             <span data-carousel-counter>1 / ${images.length}</span>
             <button type="button" data-carousel-next>›</button>
           </div>
         </div>`
      : '';
    const favoriteForm = `
      <form class="heart card-favorite-float" action="${heartAction}" method="post">
        <input type="hidden" name="_csrf" value="${csrfToken || ''}">
        <button type="submit" class="icon-btn heart-btn">${heart}</button>
      </form>`;
    const categoryBadge = category
      ? `<span class="card-badge card-badge--category">${escapeHtml(category)}</span>`
      : '';
    const ratingBadge = `<span class="card-rating-badge">★ ${(dto.ratingAverage ?? 0).toFixed(1)} · Nuevo</span>`;
    const media = gallery
      ? `<div class="card-media">
           <div class="card-media-bar">${categoryBadge}${favoriteForm}</div>
           <div class="card-media-bottom">${ratingBadge}</div>
           ${gallery}
         </div>`
      : '';
    const heartAction = dto.favorite
      ? `/favorites/${dto.id}/remove`
      : `/favorites/${dto.id}/add`;
    const heart = dto.favorite ? '♥' : '♡';
    const creatorAvatar = dto.creatorAvatar
      ? `<img class="card-creator-avatar" src="data:image/jpeg;base64,${escapeAttr(dto.creatorAvatar)}" alt="" loading="lazy">`
      : '<span class="card-creator-fallback" aria-hidden="true">✨</span>';
    const creator = dto.creatorName
      ? `<div class="card-creator">
           ${creatorAvatar}
           <span class="card-creator-name">${escapeHtml(dto.creatorName)}</span>
           <span class="card-creator-tag">armó este viaje</span>
         </div>`
      : '';
    const next = dto.nextDate || 'Sin fecha';
    const currency = dto.currencyCode || 'MXN';
    const duration = formatDuration(dto.durationMinutes || 60);
    const reservedCount = Number(dto.reservedCount || 0);
    const available = Number(dto.availableCapacity ?? 0);
    const total = available + reservedCount;
    const reservedPct = total > 0 ? Math.round((reservedCount * 100) / total) : 0;
    const capacityReserved = reservedCount > 0
      ? `<span class="card-capacity-reserved">${reservedCount} personas ya reservaron
          <span class="avatar-stack">
            ${(Array.isArray(dto.reservedAvatars) ? dto.reservedAvatars : [])
              .map((av) => `<img src="data:image/jpeg;base64,${escapeAttr(av)}" alt="" loading="lazy">`).join('')}
          </span></span>`
      : '';
    const maxCapacity = Number(dto.maxCapacity || 0);
    const occupiedPct = maxCapacity > 0 ? Math.round((reservedCount * 100) / maxCapacity) : (total > 0 ? Math.round((reservedCount * 100) / total) : 0);
    const initial = escapeHtml((dto.creatorName || 'A').charAt(0).toUpperCase());
    const mediaTopBar = `
      <div class="media-top-bar">
        <button type="button" class="media-icon-btn" aria-label="Volver" onclick="history.back()">←</button>
        <div class="media-actions">
          <button type="button" class="media-icon-btn" title="Compartir viaje" aria-label="Compartir viaje" data-share-experience="${dto.id}">↗</button>
          <form class="heart media-favorite" action="${heartAction}" method="post">
            <input type="hidden" name="_csrf" value="${csrfToken || ''}">
            <button type="submit" class="media-icon-btn heart-btn">${heart}</button>
          </form>
        </div>
      </div>`;
    const mediaBottomRating = `<div class="rating-badge"><span class="rating-star">★</span><div><span class="rating-number">${(dto.ratingAverage ?? 0).toFixed(1)}</span><span class="rating-sub">Nuevo</span></div></div>`;
    const media = gallery
      ? `<div class="card-media group">
           <div class="media-top-gradient"></div>
           ${mediaTopBar}
           ${mediaBottomRating}
           ${gallery}
         </div>`
      : '';
    return `
      <div class="card" id="card-${dto.id}">
        ${media}
        <div class="card-body-new">
          <div class="card-header-row">
            <h2 class="card-title-new">${escapeHtml(dto.name)}</h2>
            <button class="save-chip">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
              Save
            </button>
          </div>
          ${dto.creatorName ? `<div class="organizer-chip"><span class="avatar-initial">${escapeHtml(dto.creatorName.charAt(0).toUpperCase())}</span><span>Organizado por <strong>${escapeHtml(dto.creatorName)}</strong></span></div>` : ''}
          <div class="details-3col">
            <div class="detail-col"><span class="detail-icon">📅</span><strong>${next}</strong></div>
            <div class="detail-col"><span class="detail-icon">⏱</span><strong>${duration}</strong></div>
            <div class="detail-col"><span class="detail-icon">📍</span><strong>${escapeHtml(dto.meetingPointLabel || '')}</strong></div>
          </div>
          <div class="capacity-price-grid">
            <div class="capacity-block">
              <div class="cp-head">
                <span class="cp-title">Lugares libres</span>
                <span class="cp-total">${maxCapacity} cupos totales</span>
              </div>
              <div class="capacity-progress">
                <div class="capacity-fill" style="width:${occupiedPct}%"></div>
              </div>
              <div class="cp-foot">
                <div class="cp-reserved">
                  <span class="avatar-stack">
                    ${(Array.isArray(dto.reservedAvatars) ? dto.reservedAvatars : []).map((av) => `<img src="data:image/jpeg;base64,${escapeAttr(av)}" alt="" loading="lazy">`).join('')}
                  </span>
                  <span>${reservedCount} personas reservaron</span>
                </div>
                <span class="cp-free">${available} libres</span>
              </div>
            </div>
            <div class="price-block">
              <span class="price-label">Precio Total</span>
              <div class="price-value">${escapeHtml(currency)} ${dto.price}</div>
              <a class="mini-cta" href="/experiences/${dto.id}">CTA</a>
            </div>
          </div>
          <a class="reserve-full" href="/experiences/${dto.id}">
            <span class="mx-auto">Reservar ahora</span>
            <span class="reserve-icon">💳</span>
          </a>
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
