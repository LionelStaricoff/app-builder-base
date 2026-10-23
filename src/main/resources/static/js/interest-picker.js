/*
 * Selector de intereses del onboarding.
 * Sin JS el formulario sigue funcionando: la lista plana del <noscript> es el
 * formulario real. Todo lo de acá es la capa de comodidad.
 */
(function () {
    'use strict';

    var form = document.getElementById('interest-form');
    var sheet = document.getElementById('interest-sheet');
    if (!form || !sheet) {
        return;
    }

    var openBtn = document.getElementById('interest-open');
    var summary = document.getElementById('interest-summary');
    var chips = document.getElementById('interest-chips');
    var empty = document.getElementById('interest-empty');
    var counter = document.getElementById('interest-counter');
    var max = parseInt(counter && counter.dataset.max ? counter.dataset.max : '8', 10);

    var panelRubros = sheet.querySelector('[data-panel="rubros"]');
    var panelSubrubros = sheet.querySelector('[data-panel="subrubros"]');
    var rubroTitle = sheet.querySelector('[data-rubro-title]');
    var rubroCount = sheet.querySelector('[data-rubro-count]');
    var closeBtn = sheet.querySelector('[data-close]');
    var backBtn = sheet.querySelector('[data-back]');
    var boxes = Array.prototype.slice.call(form.querySelectorAll('input[name="subrubroIds"]'));

    var rubroAbierto = null;

    // --- estado -----------------------------------------------------------

    function tickeados() {
        return boxes.filter(function (b) { return b.checked; });
    }

    function pintarResumen() {
        var elegidos = tickeados();
        chips.innerHTML = '';

        elegidos.forEach(function (box) {
            var fila = box.closest('.interest-option');
            var nombre = box.dataset.name
                || (fila && fila.querySelector('.interest-option-name') ? fila.querySelector('.interest-option-name').textContent.trim() : '');

            var li = document.createElement('li');
            li.className = 'interest-chip';

            var texto = document.createElement('span');
            texto.textContent = nombre;

            var quitar = document.createElement('button');
            quitar.type = 'button';
            quitar.className = 'interest-chip-x';
            quitar.setAttribute('aria-label', 'Quitar ' + nombre);
            quitar.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" '
                + 'stroke-linecap="round" aria-hidden="true" focusable="false"><path d="M6 6l12 12M18 6L6 18"/></svg>';
            quitar.addEventListener('click', function () {
                box.checked = false;
                actualizar();
                if (openBtn) { openBtn.focus(); }
            });

            li.appendChild(texto);
            li.appendChild(quitar);
            chips.appendChild(li);
        });

        var hay = elegidos.length > 0;
        empty.hidden = hay;
        chips.hidden = !hay;
        summary.hidden = false;
        summary.classList.toggle('is-full', elegidos.length >= max);
        actualizarContador(elegidos.length);
    }

    function actualizarContador(cantidad) {
        if (!counter) { return; }
        counter.textContent = cantidad + ' de ' + max + (cantidad >= max ? ' · máximo' : '');
    }

    function actualizar() {
        pintarResumen();
        pintarBadges();
        ajustarLimite();
    }

    // Al llegar al tope deshabilitamos lo que falta tildar: es la única forma de
    // que el usuario vea el límite ANTES de mandar el formulario, no con un error.
    function ajustarLimite() {
        var cantidad = tickeados().length;
        var lleno = cantidad >= max;
        boxes.forEach(function (box) {
            var fila = box.closest('.interest-option');
            if (!fila) { return; }
            var bloquear = lleno && !box.checked;
            box.disabled = bloquear;
            fila.classList.toggle('is-disabled', bloquear);
        });
        sheet.querySelectorAll('[data-select-all]').forEach(function (btn) {
            btn.disabled = lleno;
        });
    }

    function pintarBadges() {
        sheet.querySelectorAll('[data-count-for]').forEach(function (badge) {
            var rubro = badge.dataset.countFor;
            var cantidad = boxes.filter(function (b) {
                var fila = b.closest('.interest-option');
                return b.checked && fila && fila.dataset.rubro === rubro;
            }).length;
            badge.textContent = cantidad > 0 ? String(cantidad) : '';
            badge.hidden = cantidad === 0;
        });

        if (rubroAbierto) {
            var delRubro = boxes.filter(function (b) {
                var fila = b.closest('.interest-option');
                return b.checked && fila && fila.dataset.rubro === rubroAbierto;
            }).length;
            rubroCount.textContent = delRubro > 0 ? delRubro + (delRubro === 1 ? ' elegido' : ' elegidos') : '';
            rubroCount.hidden = delRubro === 0;
        }
    }

    // --- navegación del sheet --------------------------------------------

    function abrirRubro(btn) {
        var rubro = btn.dataset.rubro;
        var subpanel = document.getElementById('panel-' + rubro);
        if (!subpanel) { return; }

        rubroAbierto = rubro;
        sheet.querySelectorAll('.interest-subpanel').forEach(function (p) { p.hidden = p !== subpanel; });
        rubroTitle.textContent = btn.querySelector('.interest-rubro-name').textContent.trim();

        panelRubros.hidden = true;
        panelSubrubros.hidden = false;
        pintarBadges();
        subpanel.scrollTop = 0;
        backBtn.focus();
    }

    function volverARubros() {
        var origen = rubroAbierto;
        rubroAbierto = null;
        panelSubrubros.hidden = true;
        panelRubros.hidden = false;
        var anterior = origen
            ? panelRubros.querySelector('.interest-rubro[data-rubro="' + origen + '"]')
            : null;
        (anterior || panelRubros.querySelector('.interest-rubro')).focus();
    }

    function abrirSheet() {
        if (typeof sheet.showModal === 'function') {
            sheet.showModal();
        } else {
            sheet.setAttribute('open', 'open');
        }
        volverARubros();
    }

    function cerrarSheet() {
        if (sheet.open && typeof sheet.close === 'function') {
            sheet.close();
        } else {
            sheet.removeAttribute('open');
        }
        if (openBtn) { openBtn.focus(); }
    }

    // --- listeners --------------------------------------------------------

    if (openBtn) {
        openBtn.hidden = false;
        openBtn.addEventListener('click', abrirSheet);
    }

    closeBtn.addEventListener('click', cerrarSheet);

    backBtn.addEventListener('click', volverARubros);

    panelRubros.addEventListener('click', function (evento) {
        var btn = evento.target.closest('.interest-rubro');
        if (btn) { abrirRubro(btn); }
    });

    // Click sobre el backdrop: el target es el propio <dialog>.
    sheet.addEventListener('click', function (evento) {
        if (evento.target === sheet) { cerrarSheet(); }
    });

    sheet.addEventListener('close', function () {
        if (openBtn) { openBtn.focus(); }
    });

    form.addEventListener('change', function (evento) {
        if (evento.target && evento.target.name === 'subrubroIds') {
            actualizar();
        }
    });

    sheet.querySelectorAll('[data-select-all]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var subpanel = btn.closest('.interest-subpanel');
            if (!subpanel) { return; }
            var propios = Array.prototype.slice.call(subpanel.querySelectorAll('input[name="subrubroIds"]'));
            var destildar = propios.every(function (b) { return b.checked; });

            if (destildar) {
                propios.forEach(function (b) { b.checked = false; });
            } else {
                for (var i = 0; i < propios.length; i++) {
                    if (tickeados().length >= max) { break; }
                    propios[i].checked = true;
                }
            }
            actualizar();
        });
    });

    form.addEventListener('submit', function () {
        var cta = form.querySelector('.interest-cta');
        if (cta && tickeados().length > 0) {
            cta.disabled = true;
            cta.textContent = 'Guardando…';
            // Re-habilitar por si el navegador vuelve con el botón atrás.
            window.setTimeout(function () {
                cta.disabled = false;
                cta.textContent = 'Continuar';
            }, 4000);
        }
    });

    if (summary) { summary.hidden = false; }
    actualizar();
})();
