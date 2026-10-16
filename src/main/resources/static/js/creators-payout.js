/**
 * Flujo de cobros del creador (Stripe Connect).
 *
 * - Reclamar: POST /api/creators/payouts/request
 *   El backend ejecuta un transfer batch a la cuenta Connect del creador.
 */
(function () {
    'use strict';

    var reclamarBtn = document.getElementById('reclamar-btn');
    var statusEl = document.getElementById('reclamar-status');

    if (reclamarBtn) {
        reclamarBtn.addEventListener('click', function () {
            reclamarBtn.disabled = true;
            statusEl.textContent = 'Procesando...';

            fetch('/api/creators/payouts/request', {
                method: 'POST',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            }).then(function (res) {
                if (!res.ok) {
                    return res.json().then(function (err) {
                        throw new Error(err.message || 'No se pudo procesar el cobro');
                    });
                }
                return res.json();
            }).then(function (data) {
                statusEl.textContent = 'Cobro procesado: $' + data.amount + ' enviados a tu cuenta.';
                setTimeout(function () {
                    window.location.reload();
                }, 2000);
            }).catch(function (e) {
                statusEl.textContent = e.message;
                reclamarBtn.disabled = false;
            });
        });
    }
})();
