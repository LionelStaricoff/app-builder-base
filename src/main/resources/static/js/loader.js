// Loader global de la app (ver fragments/loader.html y .loader en app.css).
// - SharedLoader.show(container) / hide(): overlay inline para cargas a demanda.
// - Guard automático: todo form que se envía deshabilita sus botones y les
//   pone el spinner con la etiqueta original + "…". Opt-out: data-no-loading.
(function () {
  var SVG =
    '<svg class="loader-svg loader-svg--inline" viewBox="0 0 48 48" width="16" height="16"' +
    ' aria-hidden="true" focusable="false">' +
    '<circle class="loader-track" cx="24" cy="24" r="18" fill="none" stroke-width="5"></circle>' +
    '<circle class="loader-head" cx="24" cy="24" r="18" fill="none" stroke-width="5"' +
    ' stroke-linecap="round" stroke-dasharray="28 85"></circle>' +
    '</svg>';

  function show(container, label) {
    var el = document.createElement('span');
    el.className = 'loader';
    el.setAttribute('role', 'status');
    el.setAttribute('aria-live', 'polite');
    el.innerHTML =
      SVG + '<span class="loader-label">' + (label || 'Cargando…') + '</span>';
    (container || document.body).appendChild(el);
    return el;
  }

  function hide(el) {
    el?.remove();
  }

  window.SharedLoader = { show: show, hide: hide };

  // Estado "Guardando…" al enviar cualquier form (viajes creados, reservas, config…).
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (form.dataset.noLoading === 'true') return;
    var buttons = form.querySelectorAll('button[type="submit"], button:not([type])');
    Array.prototype.forEach.call(buttons, function (b) {
      if (b.disabled) return;
      // El navegador arma la lista de campos DESPUÉS de este evento y descarta
      // los controles con el atributo `disabled`. Por eso al botón que disparó
      // el envío NO se lo deshabilita acá: se perdería su name/value y el form
      // viajaría sin ese dato (los chips de distancia mandaban "/feed?" vacío).
      // Se lo deshabilita en el tick siguiente, cuando la lista ya está armada.
      if (e.submitter === b) {
        setTimeout(function () {
          b.disabled = true;
        }, 0);
      } else {
        b.disabled = true;
      }
      b.classList.add('is-loading');
      // Sin regex: la etiqueta original puede traer los puntos suspensivos
      // que el propio loader agrega.
      var texto = b.textContent || 'Enviar';
      while (texto.endsWith('…')) {
        texto = texto.slice(0, -1);
      }
      // El botón conserva su tamaño: se mide antes de vaciarlo y se fija con
      // min-width/min-height, así el spinner no lo ensancha ni lo encoge
      // (el texto original + "…" lo estiraba).
      var caja = b.getBoundingClientRect();
      if (caja.width > 0) {
        b.style.minWidth = Math.round(caja.width) + 'px';
      }
      if (caja.height > 0) {
        b.style.minHeight = Math.round(caja.height) + 'px';
      }
      b.innerHTML = SVG;
      // La etiqueta sigue disponible para lectores de pantalla.
      b.setAttribute('aria-label', texto);
    });
  });
})();
