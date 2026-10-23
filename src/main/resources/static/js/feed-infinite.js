// Scroll infinito: pide al servidor las cards YA renderizadas.
//
// La card vive en un solo lugar (fragments/feed-card.html) y el endpoint
// /feed/cards devuelve ese mismo markup. Acá no se construye HTML: antes
// había 190 líneas duplicando la card y se desincronizaron en silencio
// (clases de carrusel viejas, meetingPointLabel inexistente, otro cálculo
// de ocupación), así que las cards 2+ se veían distintas de la primera.
(function () {
  const list = document.getElementById('feed-list');
  const sentinel = document.getElementById('feed-sentinel');
  const loading = document.getElementById('feed-loading');
  if (!list || !sentinel) return;

  const params = new URLSearchParams(window.location.search);
  let page = 1; // la 0 ya vino renderizada en el HTML
  let loadingPage = false;
  let done = false;

  async function loadNext() {
    if (loadingPage || done) return;
    loadingPage = true;
    if (loading) loading.hidden = false;
    try {
      const query = new URLSearchParams({ page: String(page) });
      ['category', 'country', 'city'].forEach((key) => {
        const value = params.get(key);
        if (value) query.set(key, value);
      });

      const res = await fetch(`/feed/cards?${query}`, {
        headers: { Accept: 'text/html' },
        credentials: 'same-origin',
      });

      // Sesión vencida: fetch sigue el redirect a /login y nos devolvería el
      // HTML del login. Sin este guard lo inyectaríamos dentro del feed.
      const type = res.headers.get('content-type') || '';
      if (!res.ok || res.redirected || !type.includes('text/html')) {
        done = true;
        return;
      }

      const html = (await res.text()).trim();
      if (!html) {
        done = true; // cuerpo vacío = no hay más páginas
        return;
      }

      list.insertAdjacentHTML('beforeend', html);
      window.initCarousels?.(list);
      window.dispatchEvent(new CustomEvent('feed-cards-added', { detail: { list } }));
      page += 1;
    } catch (e) {
      done = true;
    } finally {
      loadingPage = false;
      if (loading) loading.hidden = true;
    }
  }

  const observer = new IntersectionObserver(
    (entries) => entries.forEach((entry) => entry.isIntersecting && loadNext()),
    { rootMargin: '400px' }
  );
  observer.observe(sentinel);
})();
