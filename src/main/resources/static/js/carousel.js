// Carousel liviano sin dependencias: scroll-snap + botones + dots + contador.
document.querySelectorAll('[data-carousel]').forEach((root) => {
  const track = root.querySelector('[data-carousel-track]');
  if (!track) return;
  const slides = [...track.children];
  if (slides.length <= 1) {
    root.querySelectorAll('[data-carousel-prev],[data-carousel-next],[data-carousel-dots]').forEach((el) => el.remove());
    return;
  }
  const prev = root.querySelector('[data-carousel-prev]');
  const next = root.querySelector('[data-carousel-next]');
  const dots = root.querySelector('[data-carousel-dots]');
  const counter = root.querySelector('[data-carousel-counter]');

  slides.forEach((_, i) => {
    const d = document.createElement('span');
    if (i === 0) d.classList.add('on');
    dots?.appendChild(d);
  });

  const current = () => Math.round(track.scrollLeft / track.clientWidth);
  const go = (i) => {
    const n = (i + slides.length) % slides.length;
    track.scrollTo({ left: n * track.clientWidth, behavior: 'smooth' });
  };
  const sync = () => {
    const i = current();
    dots?.querySelectorAll('span').forEach((d, j) => d.classList.toggle('on', j === i));
    if (counter) counter.textContent = `${i + 1} / ${slides.length}`;
  };

  prev?.addEventListener('click', () => go(current() - 1));
  next?.addEventListener('click', () => go(current() + 1));
  track.addEventListener('scroll', () => requestAnimationFrame(sync), { passive: true });
  sync();
});
