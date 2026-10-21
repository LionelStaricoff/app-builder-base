// Alternancia única/recurrente + filas dinámicas + duración en horas + paradas.
(function () {
  const unique = document.getElementById('unique-fields');
  const weekly = document.getElementById('weekly-fields');
  const radios = document.querySelectorAll('input[name="recurrenceType"]');

  function sync() {
    if (!unique || !weekly) return;
    const isWeekly = document.querySelector('input[name="recurrenceType"]:checked')?.value === 'WEEKLY';
    unique.hidden = isWeekly;
    weekly.hidden = !isWeekly;
    unique.querySelectorAll('input').forEach((i) => (i.disabled = isWeekly));
    weekly.querySelectorAll('input,select').forEach((i) => (i.disabled = !isWeekly));
  }

  radios.forEach((r) => r.addEventListener('change', sync));

  const addBtn = document.getElementById('add-weekly-row');
  const rows = document.getElementById('weekly-rows');
  if (addBtn && rows) {
    addBtn.addEventListener('click', () => {
      const index = rows.querySelectorAll('.weekly-row').length;
      const template = rows.querySelector('.weekly-row').cloneNode(true);
      template.querySelectorAll('[name]').forEach((field) => {
        field.name = field.name.replace(/schedules\[\d+\]/, `schedules[${index}]`);
        if (field.type === 'time' || field.type === 'date') field.value = '';
      });
      rows.appendChild(template);
      sync();
    });
  }

  // ---- Duración visible en horas + minutos, guardada en minutos ----
  const hoursInput = document.getElementById('duration-hours');
  const minsInput = document.getElementById('duration-mins');
  const hiddenMinutes = document.getElementById('durationMinutes');
  const preview = document.getElementById('duration-preview');
  function formatDuration(total) {
    const h = Math.floor(total / 60);
    const m = total % 60;
    if (h === 0) return `${m} min`;
    if (m === 0) return h === 1 ? '1 h' : `${h} h`;
    return `${h} h ${m} min`;
  }
  function syncDurationFromHidden() {
    if (!hiddenMinutes || !hoursInput || !minsInput) return;
    const total = Math.max(1, Number.parseInt(hiddenMinutes.value || '60', 10) || 60);
    hoursInput.value = Math.floor(total / 60);
    minsInput.value = total % 60;
    if (preview) preview.textContent = formatDuration(total);
  }
  function syncHiddenFromInputs() {
    if (!hiddenMinutes || !hoursInput || !minsInput) return;
    const h = Math.max(0, Number.parseInt(hoursInput.value || '0', 10) || 0);
    const m = Math.min(59, Math.max(0, Number.parseInt(minsInput.value || '0', 10) || 0));
    const total = Math.max(1, h * 60 + m);
    hiddenMinutes.value = String(total);
    if (preview) preview.textContent = formatDuration(total);
  }
  hoursInput?.addEventListener('input', syncHiddenFromInputs);
  minsInput?.addEventListener('input', syncHiddenFromInputs);
  syncDurationFromHidden();

  // ---- Borrado selectivo de imágenes existentes (solo edición) ----
  const existingBox = document.getElementById('existing-images');
  if (existingBox) {
    const removedIds = [];
    existingBox.querySelectorAll('.img-remove').forEach((btn) => {
      btn.addEventListener('click', () => {
        const figure = btn.closest('.existing-image');
        const id = btn.dataset.imageId;
        if (id && !removedIds.includes(id)) removedIds.push(id);
        figure?.classList.add('img-removed');
        btn.disabled = true;
        let hidden = document.getElementById('remove-image-ids');
        if (!hidden) {
          const form = document.getElementById('experience-form') || document.querySelector('form');
          hidden = document.createElement('input');
          hidden.type = 'hidden';
          hidden.name = 'removeImageIds';
          hidden.id = 'remove-image-ids';
          form?.appendChild(hidden);
        }
        hidden.value = removedIds.join(',');
      });
    });
  }

  // ---- Paradas del recorrido ----
  const stopsList = document.getElementById('stops-list');
  const addStopBtn = document.getElementById('add-stop');
  const template = document.getElementById('stop-template');
  let pendingStopRow = null; // fila esperando click en el mapa

  function renumberStops() {
    if (!stopsList) return;
    stopsList.querySelectorAll('[data-stop-row]').forEach((row, i) => {
      const badge = row.querySelector('[data-stop-number]');
      if (badge) badge.textContent = String(i + 1);
    });
    window.dispatchEvent(new CustomEvent('stops-changed'));
  }

  function markPending(row) {
    stopsList?.querySelectorAll('[data-stop-row]').forEach((r) => r.classList.remove('stop-pending'));
    pendingStopRow = row;
    if (row) {
      row.classList.add('stop-pending');
      const label = row.querySelector('[data-stop-label]');
      if (label) label.textContent = 'Ahora tocá el mapa para ubicar esta parada…';
    }
  }

  function addStop(data) {
    if (!stopsList || !template) return null;
    const node = template.content.firstElementChild.cloneNode(true);
    if (data) {
      node.querySelector('input[name="stopPlace"]').value = data.place || '';
      node.querySelector('input[name="stopLat"]').value = data.lat ?? 0;
      node.querySelector('input[name="stopLng"]').value = data.lng ?? 0;
      const label = node.querySelector('[data-stop-label]');
      if (label && data.lat && data.lng) {
        label.textContent = `${Number(data.lat).toFixed(4)}, ${Number(data.lng).toFixed(4)} — clic para reubicar`;
      }
    }
    node.querySelector('[data-stop-remove]')?.addEventListener('click', () => {
      if (pendingStopRow === node) pendingStopRow = null;
      node.remove();
      renumberStops();
    });
    node.querySelector('[data-stop-up]')?.addEventListener('click', () => {
      const prev = node.previousElementSibling;
      if (prev) {
        prev.before(node);
        renumberStops();
      }
    });
    node.querySelector('[data-stop-down]')?.addEventListener('click', () => {
      const next = node.nextElementSibling;
      if (next) {
        node.before(next);
        renumberStops();
      }
    });
    // Click en la fila (fuera de inputs/botones) = quiere ubicarla en el mapa.
    node.addEventListener('click', (e) => {
      if (e.target.closest('input,button,select,textarea')) return;
      markPending(node);
    });
    // Click en el label de coords también re-activa el modo ubicar.
    node.querySelector('[data-stop-label]')?.addEventListener('click', () => markPending(node));
    stopsList.appendChild(node);
    renumberStops();
    return node;
  }

  // Exponer para map.js: al tocar el mapa ubica la fila pendiente (o la última sin ubicar).
  window.__stopsApi = {
    placeAt(lat, lng) {
      let row = pendingStopRow;
      if (!row && stopsList) {
        row = [...stopsList.querySelectorAll('[data-stop-row]')].find((r) => {
          const la = Number.parseFloat(r.querySelector('input[name="stopLat"]').value || '0');
          const lo = Number.parseFloat(r.querySelector('input[name="stopLng"]').value || '0');
          return !(la && lo);
        }) || null;
      }
      if (!row) {
        row = addStop();
        if (!row) return;
      }
      row.querySelector('input[name="stopLat"]').value = lat.toFixed(6);
      row.querySelector('input[name="stopLng"]').value = lng.toFixed(6);
      const label = row.querySelector('[data-stop-label]');
      if (label) label.textContent = `${lat.toFixed(4)}, ${lng.toFixed(4)} — clic para reubicar`;
      row.classList.remove('stop-pending');
      if (pendingStopRow === row) pendingStopRow = null;
      renumberStops();
    },
    all() {
      if (!stopsList) return [];
      return [...stopsList.querySelectorAll('[data-stop-row]')].map((r) => ({
        lat: Number.parseFloat(r.querySelector('input[name="stopLat"]').value || '0') || 0,
        lng: Number.parseFloat(r.querySelector('input[name="stopLng"]').value || '0') || 0,
        name: r.querySelector('input[name="stopPlace"]')?.value || '',
      }));
    },
    // Filas ubicadas con su índice DOM (estable aunque haya filas sin ubicar).
    located() {
      if (!stopsList) return [];
      return [...stopsList.querySelectorAll('[data-stop-row]')]
        .map((r, index) => ({
          index,
          lat: Number.parseFloat(r.querySelector('input[name="stopLat"]').value || '0') || 0,
          lng: Number.parseFloat(r.querySelector('input[name="stopLng"]').value || '0') || 0,
          name: r.querySelector('input[name="stopPlace"]')?.value || '',
        }))
        .filter((p) => p.lat && p.lng);
    },
    // Marca la fila para reubicarla con el próximo toque en el mapa.
    select(index) {
      const row = stopsList?.querySelectorAll('[data-stop-row]')[index];
      if (row) markPending(row);
    },
    // Mueve la parada (arrastre del pin en el mapa) sin cambiar el orden.
    moveTo(index, lat, lng) {
      const row = stopsList?.querySelectorAll('[data-stop-row]')[index];
      if (!row) return;
      row.querySelector('input[name="stopLat"]').value = lat.toFixed(6);
      row.querySelector('input[name="stopLng"]').value = lng.toFixed(6);
      const label = row.querySelector('[data-stop-label]');
      if (label) label.textContent = `${lat.toFixed(4)}, ${lng.toFixed(4)} — clic para reubicar`;
      row.classList.remove('stop-pending');
      if (pendingStopRow === row) pendingStopRow = null;
      window.dispatchEvent(new CustomEvent('stops-changed'));
    },
  };

  addStopBtn?.addEventListener('click', () => {
    const row = addStop();
    if (row) markPending(row);
  });

  // Prefill en edición.
  const prefill = document.getElementById('stops-prefill');
  if (prefill) {
    prefill.querySelectorAll('span').forEach((s) => {
      addStop({
        place: s.dataset.place, lat: s.dataset.lat, lng: s.dataset.lng,
      });
    });
  }

  // ---- Requeridos: marca en rojo y frena el envío si falta algo ----
  function markMissing(el) {
    el?.classList?.add('missing');
    el?.setAttribute?.('aria-invalid', 'true');
  }

  function validateRequired() {
    if (!form) return true;
    form.querySelectorAll('.missing').forEach((el) => {
      el.classList.remove('missing');
      el.removeAttribute('aria-invalid');
    });
    ['unique-error', 'weekly-error', 'duration-error'].forEach((id) => {
      const p = document.getElementById(id);
      if (p) p.hidden = true;
    });
    let first = null;
    const need = (el) => {
      if (!el) return;
      markMissing(el);
      first = first || el;
    };

    // Imágenes: entre existentes visibles y nuevas tiene que haber al menos una.
    const existingCount = existingBox
      ? existingBox.querySelectorAll('.existing-image:not(.img-removed)').length : 0;
    const imagesInput = document.getElementById('images-input');
    const newCount = imagesInput?.files?.length || 0;
    if (existingCount + newCount < 1) {
      document.getElementById('image-picker')?.classList.add('missing');
      const box = document.getElementById('image-picker-error');
      if (box) { box.textContent = 'Subí al menos una imagen.'; box.hidden = false; }
      first = first || document.getElementById('image-picker-drop');
    }

    // Duración: campos no vacíos.
    let durationOk = true;
    [hoursInput, minsInput].forEach((el) => {
      if (el?.value === '') { markMissing(el); first = first || el; durationOk = false; }
    });
    if (!durationOk) {
      const p = document.getElementById('duration-error');
      if (p) p.hidden = false;
    }

    // Agenda según el modo activo.
    const isWeekly = document.querySelector('input[name="recurrenceType"]:checked')?.value === 'WEEKLY';
    if (!isWeekly && unique) {
      let ok = true;
      ['date', 'startTime', 'endTime'].forEach((k) => {
        const f = unique.querySelector(`[name$=".${k}"]`);
        if (f && !f.value) { need(f); ok = false; }
      });
      if (!ok) {
        const p = document.getElementById('unique-error');
        if (p) p.hidden = false;
      }
    }
    if (isWeekly && rows) {
      let ok = true;
      [...rows.querySelectorAll('.weekly-row')]
        .filter((row) => ![...row.querySelectorAll('[name]')].some((f) => f.disabled))
        .forEach((row) => {
          ['startTime', 'endTime', 'rangeStart'].forEach((k) => {
            const f = row.querySelector(`[name$=".${k}"]`);
            if (f && !f.value) { need(f); ok = false; }
          });
        });
      if (!ok) {
        const p = document.getElementById('weekly-error');
        if (p) p.hidden = false;
      }
    }

    // Paradas: toda fila necesita nombre Y punto en el mapa.
    stopsList?.querySelectorAll('[data-stop-row]').forEach((row) => {
      const name = row.querySelector('input[name="stopPlace"]');
      const la = Number.parseFloat(row.querySelector('input[name="stopLat"]')?.value || '0') || 0;
      const lo = Number.parseFloat(row.querySelector('input[name="stopLng"]')?.value || '0') || 0;
      let msg = '';
      if (!name?.value.trim()) msg = 'Falta el nombre de esta parada.';
      else if (!(la && lo)) msg = 'Tocá el mapa para ubicar esta parada.';
      if (msg) {
        row.classList.add('missing');
        const label = row.querySelector('[data-stop-label]');
        if (label) label.textContent = msg;
        first = first || name;
      }
    });

    if (first) {
      first.scrollIntoView({ block: 'center' });
      first.focus?.({ preventScroll: true });
      return false;
    }
    return true;
  }

  // Renumerar solo las filas habilitadas para que los índices sean contiguos.
  const form = document.getElementById('experience-form') || document.querySelector('form');
  if (form) {
    // Limpieza en vivo: al corregir se apaga el rojo.
    form.addEventListener('input', (e) => e.target.classList?.remove('missing'));
    form.addEventListener('change', (e) => {
      e.target.classList?.remove('missing');
      if (e.target.id === 'images-input') {
        document.getElementById('image-picker')?.classList.remove('missing');
      }
    });
    form.addEventListener('submit', (e) => {
      if (!validateRequired()) {
        e.preventDefault();
        return;
      }
      syncHiddenFromInputs();
      if (rows) {
        const enabled = [...rows.querySelectorAll('.weekly-row')].filter(
          (row) => !row.querySelector('[name]').disabled
        );
        enabled.forEach((row, i) => {
          row.querySelectorAll('[name]').forEach((field) => {
            field.name = field.name.replace(/schedules\[\d+\]/, `schedules[${i}]`);
          });
        });
      }
      if (unique) {
        const uniqueRow = unique.querySelectorAll('[name]');
        uniqueRow.forEach((field) => {
          field.name = field.name.replace(/schedules\[\d+\]/, 'schedules[0]');
        });
      }
    });
  }

  sync();
})();
