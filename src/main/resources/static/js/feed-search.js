// Lupa de la cabecera del feed: abre y cierra el buscador.
// El panel arranca oculto (clase .feed-search en app.css) y el ícono
// cambia de color cuando el buscador está visible, para que se entienda
// que al volver a tocarlo se cierra.
(function () {
  'use strict';

  const btn = document.getElementById('btn-search');
  const panel = document.getElementById('feed-search');
  if (!btn || !panel) return;

  const icon = document.getElementById('search-icon');

  function setOpen(open) {
    panel.classList.toggle('is-open', open);
    btn.classList.toggle('is-active', open);
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open) {
      const input = panel.querySelector('input[name="city"]');
      if (input) {
        input.focus();
        input.select();
      }
    }
  }

  btn.addEventListener('click', function () {
    setOpen(!panel.classList.contains('is-open'));
  });

  // Si hay una búsqueda activa (el servidor la renderiza abierta), el ícono
  // arranca en su estado "encendido" para que coincida con el panel.
  if (panel.classList.contains('is-open')) {
    btn.classList.add('is-active');
    btn.setAttribute('aria-expanded', 'true');
  }

  // Escape cierra el buscador si está abierto.
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && panel.classList.contains('is-open')) {
      setOpen(false);
      btn.focus();
    }
  });

  // El ícono rota un poco al abrir para que el gesto se note.
  if (icon) {
    btn.addEventListener('click', function () {
      icon.classList.remove('transition-transform');
      void icon.offsetWidth;
      icon.classList.add('transition-transform', 'rotate-90');
    });
  }
})();
