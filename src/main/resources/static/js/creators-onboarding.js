/**
 * Flujo de onboarding de Stripe Connect.
 *
 * - Inicio: POST /api/creators/payout-account/onboarding-link?country=XX
 *   El backend valida el país contra la lista blanca de Stripe, genera un
 *   Account Link temporal y redirige a Stripe.
 */
(function () {
    'use strict';

    var TAG = '[stripe-onboarding]';
    var startBtn = document.getElementById('start-onboarding');
    if (!startBtn) { return; }

    var countrySelect = document.getElementById('payout-country');
    var countryError = document.getElementById('payout-country-error');

    var csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    function showCountryError(msg) {
        if (countryError) {
            countryError.textContent = msg;
            countryError.style.display = 'block';
        }
    }

    startBtn.addEventListener('click', function () {
        var country = countrySelect ? countrySelect.value : '';
        // Solo si el selector está visible (cuenta nueva) el país es obligatorio.
        if (countrySelect && countrySelect.offsetParent !== null && !country) {
            console.warn(TAG, 'sin país seleccionado');
            showCountryError('Elegí el país de tu cuenta bancaria para continuar.');
            return;
        }
        if (countryError) { countryError.style.display = 'none'; }

        console.log(TAG, 'click en "Configurar cuenta" — country=' + country +
            ' | csrf presente:' + !!csrfToken);
        startBtn.disabled = true;

        var url = '/api/creators/payout-account/onboarding-link' +
            (country ? '?country=' + encodeURIComponent(country) : '');

        fetch(url, {
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
                throw new Error('No se pudo generar el enlace de configuración. Revisá que tu sesión siga activa.');
            }
            if (!res.ok) {
                return res.json().then(function (err) {
                    console.warn(TAG, 'error del backend:', err);
                    throw new Error(err.message || 'No se pudo generar el enlace de configuración');
                });
            }
            return res.json();
        }).then(function (data) {
            if (!data) { return; }
            console.log(TAG, 'account link recibido, navegando a Stripe…');
            if (data && data.url) {
                window.location.href = data.url;
            }
        }).catch(function (e) {
            console.error(TAG, 'falló:', e.message);
            showCountryError(e.message);
            alert(e.message);
            startBtn.disabled = false;
        });
    });
})();
