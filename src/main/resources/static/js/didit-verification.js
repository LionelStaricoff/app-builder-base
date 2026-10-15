/**
 * Flujo de verificación de identidad con Didit (hosteado).
 *
 * - Inicio: POST /api/didit/sessions y window.location.href = session.url
 *   (redirect FULL PAGE, nunca router de SPA).
 * - Callback: Didit devuelve al usuario a /verificacion/callback; acá se
 *   REVALIDA contra el back (GET /api/didit/status) — nunca se confía en
 *   query params de Didit. El webhook puede tardar unos segundos, por eso
 *   hay polling corto.
 */
(function () {
    'use strict';

    var returnUrlInput = document.getElementById('return-url');
    var returnUrl = (returnUrlInput && returnUrlInput.value) || '/feed';

    // ---------- Página de inicio: botón "Verificar mi identidad" ----------
    var startBtn = document.getElementById('start-verification');
    if (startBtn) {
        startBtn.addEventListener('click', function () {
            startBtn.disabled = true;
            fetch('/api/didit/sessions', {
                method: 'POST',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            }).then(function (res) {
                if (res.status === 409) {
                    // Ya verificado: volver a la página de verificación.
                    window.location.href = '/verificacion';
                    return null;
                }
                if (!res.ok) {
                    throw new Error('No se pudo iniciar la verificación');
                }
                return res.json();
            }).then(function (session) {
                if (session && session.url) {
                    try { sessionStorage.setItem('didit-return-url', returnUrl); } catch (e) { /* noop */ }
                    window.location.href = session.url;
                }
            }).catch(function () {
                startBtn.disabled = false;
                alert('No se pudo iniciar la verificación. Intentá de nuevo en unos minutos.');
            });
        });
    }

    // ---------- Callback: polling contra el back ----------
    var statusEl = document.getElementById('callback-status');
    if (statusEl) {
        var tries = 0;
        var maxTries = 20; // ~30 segundos
        var poll = setInterval(function () {
            tries++;
            fetch('/api/didit/status', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(function (res) { return res.ok ? res.json() : null; })
                .then(function (status) {
                    if (!status) { throw new Error('sin status'); }
                    if (status.status === 'VERIFIED') {
                        clearInterval(poll);
                        var destino = null;
                        try { destino = sessionStorage.getItem('didit-return-url'); } catch (e) { /* noop */ }
                        window.location.href = destino || returnUrl;
                    } else if (status.status === 'REJECTED' || status.status === 'EXPIRED'
                            || tries >= maxTries) {
                        clearInterval(poll);
                        window.location.href = '/verificacion?returnUrl=' + encodeURIComponent(returnUrl);
                    }
                }).catch(function () {
                    if (tries >= maxTries) {
                        clearInterval(poll);
                        window.location.href = '/verificacion?returnUrl=' + encodeURIComponent(returnUrl);
                    }
                });
            statusEl.textContent = 'Verificando estado… (' + tries + ')';
        }, 1500);
    }
})();
