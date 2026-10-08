// Picker de imágenes para el formulario de experiencia:
// drag & drop + vista previa + validación temprana (tipo, cantidad y tamaño)
// para que el usuario vea el problema antes del POST, no después.
(function () {
  const input = document.getElementById('images-input');
  const drop = document.getElementById('image-picker-drop');
  const previews = document.getElementById('image-picker-previews');
  const errorBox = document.getElementById('image-picker-error');
  if (!input || !drop || !previews || !errorBox) return;

  const MAX_FILES = 5;
  const MAX_BYTES = 5 * 1024 * 1024;

  function showError(msg) {
    errorBox.textContent = msg;
    errorBox.hidden = false;
  }

  function clearError() {
    errorBox.hidden = true;
  }

  function render(files) {
    previews.innerHTML = '';
    [...files].forEach((file) => {
      const item = document.createElement('div');
      item.className = 'image-picker-item';

      const img = document.createElement('img');
      img.alt = file.name;
      img.src = URL.createObjectURL(file);
      img.onload = () => URL.revokeObjectURL(img.src);

      const badge = document.createElement('span');
      badge.className = 'image-picker-badge';
      badge.textContent = previews.children.length === 0 ? 'Tapa' : previews.children.length + 1;

      if (file.size > MAX_BYTES) {
        item.classList.add('too-big');
        badge.textContent = '⚠ ' + (file.size / (1024 * 1024)).toFixed(1) + ' MB';
      }

      item.appendChild(img);
      item.appendChild(badge);
      previews.appendChild(item);
    });
  }

  function setFiles(fileList) {
    const files = [...fileList];
    clearError();

    const images = files.filter((f) => f.type.startsWith('image/'));
    if (images.length !== files.length) {
      showError('Solo se permiten imágenes. Se ignoraron archivos que no lo son.');
    }
    if (images.length > MAX_FILES) {
      images.length = MAX_FILES;
      showError('Máximo ' + MAX_FILES + ' imágenes: se tomaron las primeras.');
    }
    if (images.some((f) => f.size > MAX_BYTES)) {
      showError('Hay fotos de más de 5 MB. Exportalas en un tamaño menor (en Mac: Vista previa → Ajustar tamaño).');
    }

    // DataTransfer para sincronizar el input real con lo filtrado/limitado.
    const dt = new DataTransfer();
    images.forEach((f) => dt.items.add(f));
    input.files = dt.files;
    render(images);
  }

  drop.addEventListener('click', () => input.click());
  drop.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      input.click();
    }
  });

  input.addEventListener('change', () => setFiles(input.files));

  ['dragenter', 'dragover'].forEach((evt) =>
    drop.addEventListener(evt, (e) => {
      e.preventDefault();
      drop.classList.add('dragging');
    })
  );
  ['dragleave', 'drop'].forEach((evt) =>
    drop.addEventListener(evt, (e) => {
      e.preventDefault();
      drop.classList.remove('dragging');
    })
  );
  drop.addEventListener('drop', (e) => setFiles(e.dataTransfer.files));
})();
