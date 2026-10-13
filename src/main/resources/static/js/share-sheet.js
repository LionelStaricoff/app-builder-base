// Hoja "Compartir viaje" con TODAS las redes (no una más: todas adentro).
// El botón [data-share-experience] pide /share/{id}/meta (ShareController) y
// abre la hoja: WhatsApp, Facebook, X, Telegram, LinkedIn, Pinterest, Reddit,
// Email y Copiar enlace. Todos los enlaces llevan SOLO la URL, igual que
// "Copiar enlace" (decisión de UX: nada de título pegado a la URL).
// En móvil con navigator.share usa el menú nativo (título y URL por separado).
// Accesible: role=dialog + aria-modal, Esc y clic afuera cierran, el foco
// vuelve al botón que abrió.
(function () {
  var sheet = null;
  var lastTrigger = null;

  function close() {
    if (sheet && sheet.parentNode) sheet.parentNode.removeChild(sheet);
    sheet = null;
    if (lastTrigger && document.contains(lastTrigger)) lastTrigger.focus();
    lastTrigger = null;
  }

  function buildSheet(meta) {
    var url = encodeURIComponent(meta.url);
    var opciones = [
      { label: 'WhatsApp', icon: '💬', href: 'https://wa.me/?text=' + url },
      { label: 'Facebook', icon: '📘', href: 'https://www.facebook.com/sharer/sharer.php?u=' + url },
      { label: 'X (Twitter)', icon: '𝕏', href: 'https://twitter.com/intent/tweet?text=' + url },
      { label: 'Telegram', icon: '✈️', href: 'https://t.me/share/url?url=' + url },
      { label: 'LinkedIn', icon: '💼', href: 'https://www.linkedin.com/sharing/share-offsite/?url=' + url },
      { label: 'Pinterest', icon: '📌', href: 'https://pinterest.com/pin/create/button/?url=' + url },
      { label: 'Reddit', icon: '🟠', href: 'https://www.reddit.com/submit?url=' + url },
      { label: 'Email', icon: '✉️', href: 'mailto:?body=' + url }
    ];

    var overlay = document.createElement('div');
    overlay.className = 'share-sheet-overlay';
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) close();
    });

    var dialog = document.createElement('div');
    dialog.className = 'share-sheet';
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-label', 'Compartir viaje');

    var head = document.createElement('div');
    head.className = 'share-sheet-head';
    var h = document.createElement('h3');
    h.textContent = 'Compartir viaje';
    var closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'share-sheet-close';
    closeBtn.setAttribute('aria-label', 'Cerrar');
    closeBtn.textContent = '✕';
    closeBtn.addEventListener('click', close);
    head.appendChild(h);
    head.appendChild(closeBtn);
    dialog.appendChild(head);

    var grid = document.createElement('div');
    grid.className = 'share-sheet-grid';
    opciones.forEach(function (o) {
      var a = document.createElement('a');
      a.className = 'share-sheet-option';
      a.href = o.href;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      var icon = document.createElement('span');
      icon.className = 'share-sheet-icon';
      icon.setAttribute('aria-hidden', 'true');
      icon.textContent = o.icon;
      var label = document.createElement('span');
      label.textContent = o.label;
      a.appendChild(icon);
      a.appendChild(label);
      grid.appendChild(a);
    });

    var copiar = document.createElement('button');
    copiar.type = 'button';
    copiar.className = 'share-sheet-option share-sheet-copy';
    var iconC = document.createElement('span');
    iconC.className = 'share-sheet-icon';
    iconC.setAttribute('aria-hidden', 'true');
    iconC.textContent = '🔗';
    var labelC = document.createElement('span');
    labelC.textContent = 'Copiar enlace';
    copiar.appendChild(iconC);
    copiar.appendChild(labelC);
    copiar.addEventListener('click', function () {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(meta.url).then(function () {
          labelC.textContent = '¡Copiado!';
        });
      }
    });
    grid.appendChild(copiar);
    dialog.appendChild(grid);

    overlay.appendChild(dialog);
    return { overlay: overlay, first: grid.querySelector('a, button') };
  }

  function open(trigger, meta) {
    close();
    lastTrigger = trigger;
    var built = buildSheet(meta);
    sheet = built.overlay;
    document.body.appendChild(sheet);
    if (built.first) built.first.focus();
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && sheet) close();
  });

  document.addEventListener('click', function (e) {
    var trigger = e.target && e.target.closest && e.target.closest('[data-share-experience]');
    if (!trigger) return;
    e.preventDefault();
    var id = trigger.getAttribute('data-share-experience');

    fetch('/share/' + encodeURIComponent(id) + '/meta', {
      headers: { Accept: 'application/json' }
    })
      .then(function (res) {
        if (!res.ok) throw new Error('sin meta');
        return res.json();
      })
      .then(function (meta) {
        var tactil = window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
        if (tactil && navigator.share) {
          // Nativo: título y URL por separado (jamás "título — url" pegado).
          navigator.share({ title: meta.title, url: meta.url }).catch(function () {});
          return;
        }
        open(trigger, meta);
      })
      .catch(function () {
        /* sin meta no se abre nada: el botón no rompe la card */
      });
  });
})();
