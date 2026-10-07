/* Chat STOMP por instancia: se suscribe a /topic/instances/{id} y envía por POST.
   Sin sesión (conexión rechazada por el servidor) volvemos al login. */
(function () {
  const instanceId = window.CHAT_INSTANCE_ID;
  const box = document.getElementById('chat-messages');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-text');
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  if (!instanceId || !box) return;

  function render(user, text) {
    const empty = document.getElementById('chat-empty');
    if (empty) empty.remove();
    const p = document.createElement('p');
    p.className = 'chat-msg';
    const strong = document.createElement('strong');
    strong.textContent = user;
    const span = document.createElement('span');
    span.textContent = ': ' + text;
    p.appendChild(strong);
    p.appendChild(span);
    box.appendChild(p);
    box.scrollTop = box.scrollHeight;
  }

  if (typeof SockJS !== 'undefined' && typeof Stomp !== 'undefined') {
    const client = Stomp.over(new SockJS('/ws'));
    client.debug = null;
    client.connect({}, function () {
      client.subscribe('/topic/instances/' + instanceId, function (frame) {
        try {
          const data = JSON.parse(frame.body);
          render(data.user, data.text);
        } catch (e) {
          render('', frame.body);
        }
      });
    }, function () {
      // Sin sesión o conexión rechazada: volver al login.
      window.location.href = '/login';
    });
  }

  if (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      fetch(form.action, {
        method: 'POST',
        headers: Object.assign(
          { 'Content-Type': 'application/x-www-form-urlencoded' },
          csrfHeader ? { [csrfHeader]: csrfToken } : {}
        ),
        body: 'text=' + encodeURIComponent(text),
        credentials: 'same-origin'
      }).then(function () {
        input.value = '';
      });
    });
  }
})();
