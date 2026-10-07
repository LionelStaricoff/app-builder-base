// Scroll infinito estilo Instagram: paga la siguiente página (pageSize=1)
// cuando el sentinel está por entrar en pantalla. GPS opcional por request.
(function () {
  const list = document.getElementById('feed-list');
  const sentinel = document.getElementById('feed-sentinel');
  const loading = document.getElementById('feed-loading');
  if (!list || !sentinel) return;

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  const params = new URLSearchParams(window.location.search);
  const category = params.get('category');
  let page = 1;
  let loading_page = false;
  let done = false;
  let lat = null;
  let lng = null;

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (pos) => { lat = pos.coords.latitude; lng = pos.coords.longitude; },
      () => {}
    );
  }

  function cardHtml(dto) {
    const cover = dto.coverBase64
      ? `<img class="cover" src="data:image/png;base64,${dto.coverBase64}" alt="">`
      : '';
    const heartAction = dto.favorite
      ? `/favorites/${dto.id}/remove`
      : `/favorites/${dto.id}/add`;
    const heart = dto.favorite ? '❤️' : '🤍';
    const next = dto.nextDate || 'Sin fecha';
    return `
      <div class="card" id="card-${dto.id}">
        ${cover}
        <div style="padding:1rem">
          <div class="card-head">
            <h3><a href="/experiences/${dto.id}">${escapeHtml(dto.name)}</a></h3>
            <form class="heart" action="${heartAction}" method="post">
              <input type="hidden" name="_csrf" value="${csrfToken || ''}">
              <button type="submit">${heart}</button>
            </form>
          </div>
          <p>${escapeHtml(dto.description)}</p>
          <p class="meta">📍 ${escapeHtml(dto.meetingPointLabel)} · ⏱ ${dto.durationMinutes} min · 📅 ${next} · <strong>$${dto.price} MXN</strong></p>
          <p class="meta"><span class="stars">★</span> ${(dto.ratingAverage ?? 0).toFixed(1)}</p>
          <a class="btn-primary" href="/experiences/${dto.id}">Reservar</a>
        </div>
      </div>`;
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
  }

  async function loadNext() {
    if (loading_page || done) return;
    loading_page = true;
    if (loading) loading.hidden = false;
    try {
      const query = new URLSearchParams({ page: String(page) });
      if (category) query.set('category', category);
      if (lat !== null) query.set('lat', String(lat));
      if (lng !== null) query.set('lng', String(lng));
      const res = await fetch(`/feed/api?${query}`, {
        headers: { Accept: 'application/json' },
      });
      const data = await res.json();
      const items = data.content || [];
      if (items.length === 0) {
        done = true;
      } else {
        items.forEach((dto) => list.insertAdjacentHTML('beforeend', cardHtml(dto)));
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
