/* Modal de confirmación (fragments/modal.html).
 *
 * El <dialog> nativo ya resuelve Esc, la capa top-layer y el foco inicial;
 * acá solo se conecta el disparador data-modal-open="id" (lo usa
 * experience/detail.html y experience/my-experiences.html), el clic afuera
 * de la caja y la devolución del foco al botón que abrió.
 *
 * Reemplaza a confirm(): ese era bloqueante, no estilizable y no heredaba
 * los estilos del proyecto.
 */
(function () {
    'use strict';

    function open(dialog, trigger) {
        dialog.__confirmTrigger = trigger || null;
        if (typeof dialog.showModal === 'function') {
            dialog.showModal();
        } else {
            // Fallback para navegadores sin <dialog>: mismo mecanismo que
            // interest-picker.js usa con su sheet.
            dialog.setAttribute('open', 'open');
        }
        // El foco arranca en Cancelar: si cae en Confirmar, un Enter doble
        // (abrir + confirmar) borra datos sin revisar el mensaje.
        var safe = dialog.querySelector('[data-modal-close]');
        (safe || dialog).focus();
    }

    function close(dialog) {
        if (typeof dialog.close === 'function') {
            dialog.close();
        } else {
            dialog.removeAttribute('open');
        }
        releaseFocus(dialog);
    }

    function releaseFocus(dialog) {
        if (dialog.__confirmTrigger) {
            dialog.__confirmTrigger.focus();
            dialog.__confirmTrigger = null;
        }
    }

    document.addEventListener('click', function (e) {
        var trigger = e.target.closest('[data-modal-open]');
        if (trigger) {
            var target = document.getElementById(trigger.getAttribute('data-modal-open'));
            if (target) {
                e.preventDefault();
                open(target, trigger);
            }
            return;
        }

        if (e.target.closest('[data-modal-close]')) {
            var dlg = e.target.closest('dialog');
            if (dlg) close(dlg);
            return;
        }

        // Clic en el fondo: el backdrop pertenece al propio <dialog>, así que
        // el evento llega con target === el elemento dialog.
        if (e.target.tagName === 'DIALOG') {
            close(e.target);
        }
    });

    // Esc cierra el <dialog> nativo sin pasar por close() de arriba: el
    // evento 'close' no burbujea, por eso se escucha en fase de captura.
    document.addEventListener('close', function (e) {
        if (e.target && e.target.tagName === 'DIALOG') {
            releaseFocus(e.target);
        }
    }, true);
})();
