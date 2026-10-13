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
    const total = Math.max(1, parseInt(hiddenMinutes.value || '60', 10) || 60);
    hoursInput.value = Math.floor(total / 60);
    minsInput.value = total % 60;
    if (preview) preview.textContent = formatDuration(total);
  }
  function syncHiddenFromInputs() {
    if (!hiddenMinutes || !hoursInput || !minsInput) return;
    const h = Math.max(0, parseInt(hoursInput.value || '0', 10) || 0);
    const m = Math.min(59, Math.max(0, parseInt(minsInput.value || '0', 10) || 0));
    const total = Math.max(1, h * 60 + m);
    hiddenMinutes.value = String(total);
    if (preview) preview.textContent = formatDuration(total);
  }
  hoursInput?.addEventListener('input', syncHiddenFromInputs);
  minsInput?.addEventListener('input', syncHiddenFromInputs);
  syncDurationFromHidden();

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
      node.querySelector('input[name="stopCity"]').value = data.city || '';
      node.querySelector('input[name="stopCountry"]').value = data.country || '';
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
        stopsList.insertBefore(node, prev);
        renumberStops();
      }
    });
    node.querySelector('[data-stop-down]')?.addEventListener('click', () => {
      const next = node.nextElementSibling;
      if (next) {
        stopsList.insertBefore(next, node);
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
          const la = parseFloat(r.querySelector('input[name="stopLat"]').value || '0');
          const lo = parseFloat(r.querySelector('input[name="stopLng"]').value || '0');
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
        lat: parseFloat(r.querySelector('input[name="stopLat"]').value || '0') || 0,
        lng: parseFloat(r.querySelector('input[name="stopLng"]').value || '0') || 0,
      }));
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
        place: s.dataset.place, city: s.dataset.city,
        country: s.dataset.country, lat: s.dataset.lat, lng: s.dataset.lng,
      });
    });
  }

  // Renumerar solo las filas habilitadas para que los índices sean contiguos.
  const form = document.getElementById('experience-form') || document.querySelector('form');
  if (form) {
    form.addEventListener('submit', () => {
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
