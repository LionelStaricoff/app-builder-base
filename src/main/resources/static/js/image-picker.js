// Picker de imágenes reutilizable (experiencias + avatar de perfil):
// drag & drop + vista previa + validación temprana (tipo, cantidad y tamaño).
// Cada nodo `.image-picker` se configura con data-max-files y data-max-bytes,
// así experiencias (5 x 5MB) y avatar (1 x 2MB) comparten la misma lógica.
(function () {
  var DEFAULT_MULTI_MAX_FILES = 5;
  var DEFAULT_MULTI_MAX_BYTES = 5 * 1024 * 1024;
  var DEFAULT_SINGLE_MAX_BYTES = 2 * 1024 * 1024;

  function formatMB(bytes) {
    return (bytes / (1024 * 1024)).toFixed(bytes >= 10 * 1024 * 1024 ? 0 : 1);
  }

  function initImagePicker(root) {
    if (root.dataset.pickerInit === '1') return;
    root.dataset.pickerInit = '1';

    var input = root.querySelector('input[type="file"]');
    var drop = root.querySelector('.image-picker-drop');
    var previews = root.querySelector('.image-picker-previews');
    var errorBox = root.querySelector('.image-picker-error, p[id$="-error"], .error');
    if (!input || !drop || !previews || !errorBox) return;

    var isSingle = !input.hasAttribute('multiple');
    var maxFiles = parseInt(root.dataset.maxFiles || '', 10);
    if (isNaN(maxFiles) || maxFiles < 1) {
      maxFiles = isSingle ? 1 : DEFAULT_MULTI_MAX_FILES;
    }
    var maxBytes = parseInt(root.dataset.maxBytes || '', 10);
    if (isNaN(maxBytes) || maxBytes < 1) {
      maxBytes = maxFiles === 1 ? DEFAULT_SINGLE_MAX_BYTES : DEFAULT_MULTI_MAX_BYTES;
    }
    var maxLabel = root.dataset.maxLabel || formatMB(maxBytes) + ' MB';

    function showError(msg) {
      errorBox.textContent = msg;
      errorBox.hidden = false;
    }

    function clearError() {
      errorBox.hidden = true;
    }

    function render(files) {
      previews.innerHTML = '';
      Array.prototype.forEach.call(files, function (file, index) {
        var item = document.createElement('div');
        item.className = 'image-picker-item';

        var img = document.createElement('img');
        img.alt = file.name;
        img.src = URL.createObjectURL(file);
        img.onload = function () { URL.revokeObjectURL(img.src); };

        var badge = document.createElement('span');
        badge.className = 'image-picker-badge';
        if (maxFiles === 1) {
          badge.textContent = 'Nuevo avatar';
        } else {
          badge.textContent = index === 0 ? 'Tapa' : String(index + 1);
        }

        if (file.size > maxBytes) {
          item.classList.add('too-big');
          badge.textContent = '⚠ ' + formatMB(file.size) + ' MB';
        }

        item.appendChild(img);
        item.appendChild(badge);
        previews.appendChild(item);
      });
    }

    function setFiles(fileList) {
      var files = Array.prototype.slice.call(fileList || []);
      clearError();

      var images = files.filter(function (f) { return f.type.indexOf('image/') === 0; });
      if (images.length !== files.length) {
        showError('Solo se permiten imágenes. Se ignoraron archivos que no lo son.');
      }
      if (images.length > maxFiles) {
        images.length = maxFiles;
        showError(maxFiles === 1
          ? 'Solo se permite 1 imagen: se tomó la primera.'
          : 'Máximo ' + maxFiles + ' imágenes: se tomaron las primeras.');
      }
      if (images.some(function (f) { return f.size > maxBytes; })) {
        showError('La imagen supera los ' + maxLabel + '. Exportala en un tamaño menor (en Mac: Vista previa → Ajustar tamaño).');
      }

      // DataTransfer para sincronizar el input real con lo filtrado/limitado.
      var dt = new DataTransfer();
      images.forEach(function (f) { dt.items.add(f); });
      input.files = dt.files;
      render(images);
    }

    drop.addEventListener('click', function () { input.click(); });
    drop.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        input.click();
      }
    });

    input.addEventListener('change', function () { setFiles(input.files); });

    ['dragenter', 'dragover'].forEach(function (evt) {
      drop.addEventListener(evt, function (e) {
        e.preventDefault();
        drop.classList.add('dragging');
      });
    });
    ['dragleave', 'drop'].forEach(function (evt) {
      drop.addEventListener(evt, function (e) {
        e.preventDefault();
        drop.classList.remove('dragging');
      });
    });
    drop.addEventListener('drop', function (e) { setFiles(e.dataTransfer.files); });
  }

  document.querySelectorAll('.image-picker').forEach(initImagePicker);
})();
