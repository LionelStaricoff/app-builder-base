/**
 * Flujo de onboarding de Stripe Connect.
 *
 * - Inicio: POST /api/creators/payout-account/onboarding-link
 *   El backend genera un Account Link temporal y redirige a Stripe.
 */
(function () {
    'use strict';

    var startBtn = document.getElementById('start-onboarding');

    if (startBtn) {
        startBtn.addEventListener('click', function () {
            startBtn.disabled = true;

            fetch('/api/creators/payout-account/onboarding-link', {
                method: 'POST',
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            }).then(function (res) {
                if (!res.ok) {
                    throw new Error('No se pudo generar el enlace de configuración');
                }
                return res.json();
            }).then(function (data) {
                if (data && data.url) {
                    window.location.href = data.url;
                }
            }).catch(function (e) {
                alert(e.message);
                startBtn.disabled = false;
            });
        });
    }
})();
