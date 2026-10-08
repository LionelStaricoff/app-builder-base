// NUNCA tragar el error en silencio: un 404 acá rompe la instalabilidad
// y con .catch(() => {}) no te enterás jamás.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((e) => console.warn('SW registration:', e));
  });
}
