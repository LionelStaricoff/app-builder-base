/**
 * Flujo de cobros del creador (Stripe Connect).
 *
 * - Reclamar: POST /api/creators/payouts/request
 *   El backend ejecuta un transfer batch a la cuenta Connect del creador.
 */
(function () {
    'use strict';

    var TAG = '[stripe-payout]';
    var reclamarBtn = document.getElementById('reclamar-btn');
    if (!reclamarBtn) { return; }

    var statusEl = document.getElementById('reclamar-status');
    var csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    reclamarBtn.addEventListener('click', function () {
        console.log(TAG, 'click en "Reclamar" — csrf presente:', !!csrfToken);
        reclamarBtn.disabled = true;
        statusEl.textContent = 'Procesando...';

        fetch('/api/creators/payouts/request', {
            method: 'POST',
            headers: Object.assign(
                { 'X-Requested-With': 'XMLHttpRequest' },
                csrfHeader ? { [csrfHeader]: csrfToken } : {}
            )
        }).then(function (res) {
            console.log(TAG, 'respuesta:', res.status,
                '| ok=' + res.ok, '| redirected=' + res.redirected,
                '| url=' + res.url,
                '| content-type=' + (res.headers.get('content-type') || 'sin header'));
            if (res.redirected) {
                // El back respondió un redirect (login/verificación): seguirlo.
                console.log(TAG, 'redirect detectado → navegando a', res.url);
                window.location.href = res.url;
                return null;
            }
            if ((res.headers.get('content-type') || '').indexOf('application/json') === -1) {
                // Respuesta no JSON (página HTML): no intentar parsearla.
                console.warn(TAG, 'respuesta NO JSON, abortando parseo');
                throw new Error('No se pudo procesar el cobro. Revisá que tu sesión siga activa.');
            }
            if (!res.ok) {
                return res.json().then(function (err) {
                    console.warn(TAG, 'error del backend:', err);
                    throw new Error(err.message || 'No se pudo procesar el cobro');
                });
            }
            return res.json();
        }).then(function (data) {
            if (!data) { return; }
            console.log(TAG, 'cobro procesado:', data);
            statusEl.textContent = 'Cobro procesado: $' + data.amount + ' enviados a tu cuenta.';
            setTimeout(function () {
                window.location.reload();
            }, 2000);
        }).catch(function (e) {
            console.error(TAG, 'falló:', e.message);
            statusEl.textContent = e.message;
            reclamarBtn.disabled = false;
        });
    });
})();
