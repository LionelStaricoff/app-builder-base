// Card del feed: fechas, cupos y secciones lazy.
//
// Todo se delega en document porque el scroll infinito inyecta cards nuevas
// después del render inicial: un listener por card dejaría las cards 2+ sin
// comportamiento. La card no crea reservas: el CTA es un GET al detalle con la
// fecha y los cupos elegidos.
(function () {
  function esHtml(res) {
    const tipo = res.headers.get('content-type') || '';
    return res.ok && !res.redirected && tipo.includes('text/html');
  }

  async function fetchFragment(url, options) {
    const opciones = Object.assign({ credentials: 'same-origin' }, options || {});
    opciones.headers = Object.assign(
      { Accept: 'text/html' },
      (options && options.headers) || {}
    );
    const res = await fetch(url, opciones);
    if (!esHtml(res)) {
      throw new Error('No se pudo cargar la sección');
    }
    return (await res.text()).trim();
  }

  function actualizarContador(details, contenido) {
    const origen = contenido.querySelector('[data-count]');
    const badge = details ? details.querySelector('[data-lazy-count]') : null;
    if (origen && badge) {
      badge.textContent = '(' + origen.dataset.count + ')';
    }
  }

  function marcarSeleccion(panel, elegido) {
    panel.querySelectorAll('[data-date-option]').forEach((boton) => {
      const activo = boton === elegido;
      boton.classList.toggle('ring-2', activo);
      boton.classList.toggle('ring-teal-300', activo);
      boton.classList.toggle('bg-teal-50', activo);
      boton.classList.toggle('bg-white', !activo);
      boton.setAttribute('aria-pressed', activo ? 'true' : 'false');
    });
  }

  async function alternarFechas(boton) {
    const card = boton.closest('.feed-card');
    const panel = card ? card.querySelector('[data-dates-panel]') : null;
    if (!card || !panel) return;

    const abrir = panel.hidden;
    if (abrir && panel.dataset.loaded !== '1') {
      const experiencia = card.dataset.experienceId;
      const actual = card.querySelector('[data-card-instance-id]');
      const query = actual && actual.value
        ? '?instanceId=' + encodeURIComponent(actual.value)
        : '';
      try {
        panel.innerHTML = await fetchFragment(
          '/experiences/' + encodeURIComponent(experiencia) + '/dates' + query
        );
        panel.dataset.loaded = '1';
      } catch (_) {
        panel.innerHTML =
          '<p class="text-[11px] text-rose-600 px-1 py-2">No se pudieron cargar las fechas.</p>';
        panel.hidden = false;
        boton.setAttribute('aria-expanded', 'true');
        return;
      }
    }
    panel.hidden = !abrir;
    boton.setAttribute('aria-expanded', abrir ? 'true' : 'false');
  }

  async function elegirFecha(opcion) {
    const card = opcion.closest('.feed-card');
    if (!card) return;

    const panel = card.querySelector('[data-dates-panel]');
    const botonFechas = card.querySelector('[data-dates-toggle]');
    const capacidad = card.querySelector('[data-card-capacity]');
    const oculto = card.querySelector('[data-card-instance-id]');
    const reserva = card.querySelector('form[data-card-booking]');
    const experiencia = card.dataset.experienceId;
    const instanceId = opcion.dataset.instanceId;
    const etiqueta = opcion.dataset.dateLabel || '';

    opcion.disabled = true;
    try {
      const html = await fetchFragment(
        '/experiences/' + encodeURIComponent(experiencia) + '/dates/' +
        encodeURIComponent(instanceId)
      );
      if (capacidad) {
        capacidad.innerHTML = html;
        capacidad.dataset.instanceId = instanceId;
      }
      card.querySelectorAll('[data-card-date-label]').forEach((el) => {
        el.textContent = etiqueta;
      });
      if (oculto) {
        oculto.value = instanceId;
      }
      // El CTA es un POST a /reservations/{instanceId}: si cambia la fecha,
      // cambia el destino. Sin esto se reservaría la fecha anterior.
      if (reserva) {
        reserva.action = '/reservations/' + encodeURIComponent(instanceId);
      }
      if (panel) {
        marcarSeleccion(panel, opcion);
        panel.hidden = true;
      }
      if (botonFechas) {
        botonFechas.setAttribute('aria-expanded', 'false');
      }
    } catch (_) {
      if (panel) {
        panel.innerHTML =
          '<p class="text-[11px] text-rose-600 px-1 py-2">No se pudo cambiar la fecha.</p>';
        panel.hidden = false;
      }
      if (botonFechas) {
        botonFechas.setAttribute('aria-expanded', 'true');
      }
    } finally {
      opcion.disabled = false;
    }
  }

  document.addEventListener('click', (event) => {
    const boton = event.target.closest('[data-dates-toggle]');
    if (boton) {
      event.preventDefault();
      alternarFechas(boton);
      return;
    }
    const opcion = event.target.closest('[data-date-option]');
    if (opcion) {
      event.preventDefault();
      elegirFecha(opcion);
    }
  });

  // El evento toggle NO burbujea: hay que escucharlo en captura. Se filtra
  // por details[data-lazy-section] para no tocar los <details> nativos de
  // recorrido/incluye/llevar.
  document.addEventListener('toggle', async (event) => {
    const details = event.target;
    if (!(details instanceof HTMLDetailsElement) ||
        !details.matches('details[data-lazy-section]')) {
      return;
    }
    if (!details.open || details.dataset.loaded === '1' ||
        details.dataset.loading === '1') {
      return;
    }
    const contenido = details.querySelector('[data-lazy-content]');
    if (!contenido) return;

    details.dataset.loading = '1';
    try {
      contenido.innerHTML = await fetchFragment(details.dataset.lazyUrl);
      actualizarContador(details, contenido);
      details.dataset.loaded = '1';
    } catch (_) {
      contenido.innerHTML =
        '<p class="text-[11px] text-rose-600 py-2">No se pudo cargar la sección.</p>';
    } finally {
      delete details.dataset.loading;
    }
  }, true);

  document.addEventListener('submit', async (event) => {
    const form = event.target.closest('form[data-question-form]');
    if (!form) return;
    event.preventDefault();

    const details = form.closest('details[data-lazy-section]');
    const contenido = form.closest('[data-lazy-content]');
    if (!contenido || !form.dataset.fragmentAction) return;

    const boton = form.querySelector('button[type="submit"]');
    if (boton) boton.disabled = true;
    try {
      contenido.innerHTML = await fetchFragment(form.dataset.fragmentAction, {
        method: 'POST',
        body: new FormData(form),
      });
      actualizarContador(details, contenido);
    } catch (_) {
      contenido.innerHTML =
        '<p class="text-[11px] text-rose-600 py-2">No se pudo enviar la pregunta.</p>';
    }
  });
})();
