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
    const heartAction = dto.favorite
      ? `/favorites/${dto.id}/remove`
      : `/favorites/${dto.id}/add`;
    const heart = dto.favorite ? 'fill-rose-500 stroke-rose-500' : '';
    const creatorAvatar = dto.creatorAvatar
      ? `<img src="data:image/jpeg;base64,${escapeAttr(dto.creatorAvatar)}" alt="" class="w-full h-full object-cover">`
      : `<span class="text-white">${escapeHtml((dto.creatorName || 'A').charAt(0).toUpperCase())}</span>`;
    const next = dto.nextDate || 'Sin fecha';
    const currency = dto.currencyCode || 'COP';
    const duration = formatDuration(dto.durationMinutes || 60);
    const reservedCount = Number(dto.reservedCount || 0);
    const available = Number(dto.availableCapacity ?? 0);
    const total = available + reservedCount;
    const maxCapacity = Number(dto.maxCapacity || 0);
    const occupiedPct = maxCapacity > 0 ? Math.round((reservedCount * 100) / maxCapacity) : 0;

    const stops = Array.isArray(dto.stops) ? dto.stops : [];
    const stopsHtml = stops.length > 0
      ? `<div class="border-b border-gray-100 pb-2">
            <button type="button" 
                    onclick="this.nextElementSibling.classList.toggle('hidden')"
                    class="w-full flex items-center justify-between py-2 text-[10px] font-bold text-gray-400 uppercase tracking-wider hover:text-gray-600 transition-colors">
              <div class="flex items-center gap-2">
                <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M9 20l-5.447-2.89M15 20l5.447-2.89M12 20V4"/></svg>
                Ver Recorrido
              </div>
              <svg class="w-3 h-3 transform transition-transform" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/></svg>
            </button>
            <div class="hidden space-y-2 py-2 px-1">
              ${stops.map((stop, idx) => `
                <div class="flex items-start gap-3">
                  <div class="flex flex-col items-center">
                    <div class="w-2 h-2 rounded-full bg-teal-600 ${idx === 0 ? 'ring-4 ring-teal-100' : ''} mt-1"></div>
                    ${idx !== stops.length - 1 ? '<div class="w-px h-4 bg-gray-200"></div>' : ''}
                  </div>
                  <div class="flex flex-col">
                    <span class="text-[11px] font-bold text-gray-800">${escapeHtml(stop.placeName)}</span>
                    <span class="text-[9px] text-gray-500">${escapeHtml(stop.city)}, ${escapeHtml(stop.country)}</span>
                  </div>
                </div>
              `).join('')}
            </div>
          </div>`
      : '';

    return `
      <div class="card max-w-md mx-auto bg-white rounded-3xl shadow-xl border border-gray-100 overflow-hidden font-sans mb-6" style="border: 1px solid var(--border-subtle)" id="card-${dto.id}">
        <div class="relative h-72 w-full overflow-hidden group">
          <img src="data:image/jpeg;base64,${dto.coverBase64 || ''}" 
               alt="Experiencia" 
               class="w-full h-full object-cover">
          <div class="absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-black/50 to-transparent pointer-events-none z-10"></div>
          <div class="absolute top-4 left-4 z-20">
            <button class="bg-white/90 hover:bg-white text-gray-800 p-2.5 rounded-full backdrop-blur-md shadow-md transition-all active:scale-95 flex items-center justify-center" onclick="history.back()">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/></svg>
            </button>
          </div>
          <div class="absolute top-4 right-4 flex items-center gap-2 z-20">
            <button class="bg-white/90 hover:bg-white text-gray-800 p-2.5 rounded-full backdrop-blur-md shadow-md transition-all active:scale-95 flex items-center justify-center" data-share-experience="${dto.id}">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"/></svg>
            </button>
            <form action="${heartAction}" method="post" class="inline">
              <input type="hidden" name="_csrf" value="${csrfToken || ''}">
              <button type="submit" class="bg-white/90 hover:bg-white text-gray-800 p-2.5 rounded-full backdrop-blur-md shadow-md transition-all active:scale-95 flex items-center justify-center">
                <svg class="w-4 h-4 ${heart}" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
              </button>
            </form>
          </div>
          <div class="absolute bottom-4 left-4 bg-white/95 backdrop-blur-md px-3 py-1.5 rounded-2xl shadow-lg flex items-center gap-2 z-20 border border-white/40">
            <span class="text-amber-500 text-base">★</span>
            <div class="flex flex-col leading-none">
              <span class="text-xs font-bold text-gray-900">${(dto.ratingAverage || 0).toFixed(1)}</span>
              <span class="text-[10px] text-gray-500 font-medium">${dto.ratingAverage > 0 ? '(' + dto.reviewCount + ')' : 'Nuevo'}</span>
            </div>
          </div>
          ${gallery ? `
          <div class="absolute inset-0 z-10" data-carousel>
            <div class="relative h-full w-full overflow-hidden" data-carousel-track>
              <div class="flex h-full transition-transform duration-300 ease-out">${slides}</div>
            </div>
            <div class="absolute bottom-4 right-4 bg-black/50 backdrop-blur-md text-white text-xs font-medium px-3.5 py-1.5 rounded-full flex items-center gap-3 shadow-lg border border-white/20 z-20">
              <button type="button" data-carousel-prev class="hover:text-amber-400 transition-colors font-bold text-sm">‹</button>
              <span class="tracking-widest" data-carousel-counter>1 / ${images.length}</span>
              <button type="button" data-carousel-next class="hover:text-amber-400 transition-colors font-bold text-sm">›</button>
            </div>
          </div>` : ''}
        </div>
        <div class="p-5 space-y-4">
          <div class="flex items-start justify-between gap-2">
            <div class="max-w-[75%]">
              <h2 class="text-xl font-black text-gray-900 tracking-tight leading-tight">${escapeHtml(dto.name)}</h2>
            </div>
            <button class="flex items-center gap-1.5 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-semibold px-3 py-1 rounded-full transition-colors shadow-sm">
              <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
              Save
            </button>
          </div>
          ${dto.creatorName ? `
          <div class="inline-flex items-center gap-2 bg-gray-100/80 px-2.5 py-1 rounded-full text-[11px] text-gray-700 font-medium">
            <div class="w-4 h-4 rounded-full bg-gray-900 text-white flex items-center justify-center text-[8px] font-bold overflow-hidden">${creatorAvatar}</div>
            <span>Organizado por <strong class="text-gray-900">${escapeHtml(dto.creatorName)}</strong></span>
          </div>` : ''}
          <div class="grid grid-cols-3 gap-2 py-2 text-center text-[11px] text-gray-600 border-y border-gray-100">
            <div class="flex flex-col items-center gap-1">
              <svg class="w-4 h-4 text-gray-700" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
              <span class="font-bold text-gray-900">${next}</span>
            </div>
            <div class="flex flex-col items-center gap-1 border-x border-gray-100 px-2">
              <svg class="w-4 h-4 text-gray-700" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
              <span class="font-bold text-gray-900">${duration}</span>
            </div>
            <div class="flex flex-col items-center gap-1">
              <a href="https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(dto.meetingPointLabel || '')}" target="_blank" class="flex flex-col items-center gap-1 hover:text-teal-600 transition-colors group">
                <svg class="w-4 h-4 text-gray-700 group-hover:text-teal-600" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"/></svg>
                <span class="font-bold text-gray-900 truncate w-full group-hover:text-teal-600">${escapeHtml(dto.meetingPointLabel || '')}</span>
              </a>
            </div>
          </div>
          ${stopsHtml}
          <div class="grid grid-cols-5 gap-3">
            <div class="col-span-3 bg-gray-50 p-3 rounded-2xl border border-gray-100 flex flex-col justify-between space-y-2">
              <div class="flex justify-between items-center text-[11px]">
                <span class="font-semibold text-gray-800">Lugares libres</span>
                <span class="text-gray-400 font-medium text-[10px]">${maxCapacity} totales</span>
              </div>
              <div class="w-full bg-gray-200 h-1.5 rounded-full overflow-hidden">
                <div class="bg-teal-700 h-full rounded-full transition-all duration-300" style="width:${occupiedPct}%"></div>
              </div>
              <div class="flex items-center justify-between text-[10px] text-gray-500">
                <div class="flex items-center gap-1.5">
                  <div class="flex -space-x-1.5">
                    ${(Array.isArray(dto.reservedAvatars) ? dto.reservedAvatars : []).map((av) => `<img src="data:image/jpeg;base64,${escapeAttr(av)}" class="w-3.5 h-3.5 rounded-full ring-1 ring-white object-cover" alt="">`).join('')}
                  </div>
                  <span>${reservedCount} reservaron</span>
                </div>
                <span class="font-bold text-teal-700">${available} libres</span>
              </div>
            </div>
            <div class="col-span-2 bg-gray-50 p-3 rounded-2xl border border-gray-100 flex flex-col justify-between text-center">
              <span class="text-[8px] font-extrabold uppercase tracking-wider text-gray-400">Precio Total</span>
              <div class="my-auto">
                <div class="text-gray-900 font-black text-xs tracking-tight">${escapeHtml(currency)} ${dto.price}</div>
                <span class="text-[9px] font-semibold text-gray-500">COP</span>
              </div>
              <a class="w-full bg-rose-600 hover:bg-rose-700 text-white text-[10px] font-bold py-1 rounded-lg shadow-sm transition-all active:scale-95 block text-center" href="/experiences/${dto.id}">CTA</a>
            </div>
          </div>
          <a class="w-full bg-gray-100 hover:bg-gray-200 active:scale-[0.99] text-gray-900 font-bold py-3 px-5 rounded-2xl flex items-center justify-between border border-gray-200 transition-all shadow-sm" href="/experiences/${dto.id}">
            <span class="mx-auto text-sm">Reservar ahora</span>
            <div class="flex items-center gap-1.5 opacity-60">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a2 2 0 002-2V7a2 2 0 00-2-2H6a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>
            </div>
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
        list.querySelectorAll('[data-map="feed-route"]:not([data-map-init])').forEach((el) => {
          el.dataset.mapInit = '1';
        });
        window.dispatchEvent(new CustomEvent('feed-cards-added', { detail: { list } }));
        page += 1;
      }
    } catch (e) {
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