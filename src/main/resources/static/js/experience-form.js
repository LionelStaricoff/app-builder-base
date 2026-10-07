// Alternancia única/recurrente + filas dinámicas de días.
(function () {
  const unique = document.getElementById('unique-fields');
  const weekly = document.getElementById('weekly-fields');
  const radios = document.querySelectorAll('input[name="recurrenceType"]');

  function sync() {
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

  // Renumerar solo las filas habilitadas para que los índices sean contiguos.
  const form = document.querySelector('form');
  if (form) {
    form.addEventListener('submit', () => {
      const enabled = [...rows.querySelectorAll('.weekly-row')].filter(
        (row) => !row.querySelector('[name]').disabled
      );
      enabled.forEach((row, i) => {
        row.querySelectorAll('[name]').forEach((field) => {
          field.name = field.name.replace(/schedules\[\d+\]/, `schedules[${i}]`);
        });
      });
      const uniqueRow = unique.querySelectorAll('[name]');
      uniqueRow.forEach((field) => {
        field.name = field.name.replace(/schedules\[\d+\]/, 'schedules[0]');
      });
    });
  }

  sync();
})();
