// Carrusel horizontal de categorías del feed.
// La tira oculta el scrollbar por diseño (clase no-scrollbar), así que la
// rueda del mouse (vertical) no la desplazaba sola: acá se mapea a
// desplazamiento horizontal. Además se puede arrastrar con el mouse.
(function () {
  'use strict';

  const track = document.getElementById('category-container');
  if (!track) return;

  const canScroll = () => track.scrollWidth > track.clientWidth + 1;

  // Rueda vertical → desplazamiento horizontal de la tira.
  track.addEventListener('wheel', (e) => {
    if (!canScroll()) return; // no hay nada que desplazar: que scrollee la página
    if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return; // ya es horizontal (trackpad)
    const max = track.scrollWidth - track.clientWidth;
    const next = track.scrollLeft + e.deltaY;
    // En los extremos se deja pasar el scroll a la página (no queda trapped).
    if ((e.deltaY < 0 && track.scrollLeft <= 0) || (e.deltaY > 0 && next >= max)) return;
    e.preventDefault();
    track.scrollLeft = next;
  }, { passive: false });

  // Arrastre con el mouse para desplazar la tira.
  let dragging = false;
  let moved = false;
  let startX = 0;
  let startScroll = 0;

  track.addEventListener('pointerdown', (e) => {
    if (e.pointerType === 'touch') return; // en táctil el scroll nativo alcanza
    if (!canScroll()) return;
    dragging = true;
    moved = false;
    startX = e.clientX;
    startScroll = track.scrollLeft;
    track.style.cursor = 'grabbing';
    track.setPointerCapture(e.pointerId);
  });

  track.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dx = e.clientX - startX;
    if (Math.abs(dx) > 4) moved = true;
    track.scrollLeft = startScroll - dx;
  });

  function endDrag(e) {
    if (!dragging) return;
    dragging = false;
    track.style.cursor = '';
    try {
      track.releasePointerCapture(e.pointerId);
    } catch (err) {
      // el puntero ya se había liberado: nada que hacer
    }
  }

  track.addEventListener('pointerup', endDrag);
  track.addEventListener('pointercancel', endDrag);

  // Un arrastre no debe disparar el click de la categoría.
  track.addEventListener('click', (e) => {
    if (moved) {
      e.preventDefault();
      e.stopPropagation();
      moved = false;
    }
  }, true);

  track.style.cursor = canScroll() ? 'grab' : '';
  window.addEventListener('resize', () => {
    track.style.cursor = canScroll() ? 'grab' : '';
  });
})();
