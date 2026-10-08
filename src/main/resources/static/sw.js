const CACHE = 'shared-shell-v3';
const SHELL = ['/css/app.css', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((c) =>
      Promise.all(
        SHELL.map((url) =>
          c.add(url).catch(() => null)
        )
      )
    )
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/ws') || event.request.method !== 'GET') return;

  // Shell cache-first (funciona sin conexión).
  if (SHELL.includes(url.pathname)) {
    event.respondWith(caches.match(event.request).then((r) => r || fetch(event.request)));
    return;
  }

  // Navegaciones y datos: network-first con caché como respaldo + fallback offline.
  event.respondWith(
    fetch(event.request)
      .then((res) => {
        if (res.ok && url.pathname === '/') {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put('/', copy));
        }
        return res;
      })
      .catch(() =>
        caches.match(event.request).then(
          (cached) =>
            cached ||
            (event.request.mode === 'navigate' ? caches.match('/') : null) ||
            new Response('Sin conexión', {
              status: 503,
              headers: { 'Content-Type': 'text/plain;charset=utf-8' }
            })
        )
      )
  );
});
