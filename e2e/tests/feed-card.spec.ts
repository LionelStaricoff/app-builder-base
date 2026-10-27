import { expect, test, type Page } from '@playwright/test';
import { CERCA, USER, login } from './helpers';

/**
 * Lo que la suite MockMvc no puede ver: el JS de la card armando requests.
 *
 * La card pide fragmentos HTML (fechas, cupos, preguntas, reseñas) y no recarga
 * la página. Si el cliente arma mal la URL, no escucha el evento `toggle` o
 * pierde el estado al reemplazar un bloque, el servidor responde 200 igual y la
 * suite Java queda verde. Esto se prueba en un navegador real.
 */

async function cardCerca(page: Page) {
  // Radio de 1 km: queda una sola card y la selección no depende del orden.
  await page.goto('/feed?distance=1');
  const card = page.locator('.feed-card', { hasText: CERCA });
  await expect(card).toBeVisible();
  return card;
}

test.describe('card del feed', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, USER);
  });

  test('el botón de fecha cambia la fecha y refresca los cupos sin recargar', async ({ page }) => {
    const card = await cardCerca(page);
    // Marca en el objeto window: si la página se recargara, desaparece.
    await page.evaluate(() => {
      (window as unknown as { __sinRecarga?: boolean }).__sinRecarga = true;
    });

    const label = card.locator('[data-card-date-label]');
    const inicial = (await label.innerText()).trim();
    const toggle = card.locator('[data-dates-toggle]');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await toggle.click();
    const panel = card.locator('[data-dates-panel]');
    await expect(panel).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');

    const opciones = panel.locator('[data-date-option]');
    await expect(opciones).toHaveCount(2);
    // La fecha que ya mostraba la card viene marcada como seleccionada.
    await expect(opciones.nth(0)).toHaveAttribute('data-date-label', inicial);

    const segunda = opciones.nth(1);
    const nuevaFecha = (await segunda.getAttribute('data-date-label'))!;
    const nuevoInstanceId = (await segunda.getAttribute('data-instance-id'))!;

    await segunda.click();

    await expect(panel).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(label).toHaveText(nuevaFecha);
    await expect(card.locator('[data-card-capacity]')).toHaveAttribute(
      'data-instance-id',
      nuevoInstanceId,
    );
    await expect(card.locator('input[name="instanceId"]')).toHaveValue(nuevoInstanceId);
    // El input de cupos vive fuera del bloque que se reemplaza: sobrevive.
    await expect(card.locator('input[name="attendees"]')).toHaveValue('1');

    expect(
      await page.evaluate(() => (window as unknown as { __sinRecarga?: boolean }).__sinRecarga),
    ).toBe(true);
  });

  test('las secciones lazy piden el fragmento una sola vez', async ({ page }) => {
    const pedidos: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/questions')) pedidos.push(request.url());
    });

    const card = await cardCerca(page);
    const preguntas = card.locator('details[data-lazy-section]').first();
    const cuerpo = preguntas.locator('[data-lazy-content]');
    await expect(cuerpo).toContainText('Tocá para cargar');

    await preguntas.locator('summary').click();
    await expect(cuerpo).not.toContainText('Tocá para cargar');
    await expect(preguntas.locator('[data-lazy-count]')).toHaveText(/^\(\d+\)$/);

    // Cerrar y volver a abrir NO debe repetir el request (data-loaded).
    await preguntas.locator('summary').click();
    await expect(preguntas).not.toHaveAttribute('open', '');
    await preguntas.locator('summary').click();
    await expect(cuerpo).not.toContainText('Tocá para cargar');

    expect(pedidos, 'un solo GET /questions para la card').toHaveLength(1);
  });

  test('preguntar desde la card muestra la pregunta sin salir del feed', async ({ page }) => {
    const card = await cardCerca(page);
    const preguntas = card.locator('details[data-lazy-section]').first();
    await preguntas.locator('summary').click();

    const texto = `Pregunta e2e ${Date.now()}`;
    await preguntas.locator('textarea[name="body"]').fill(texto);
    await preguntas.getByRole('button', { name: 'Preguntar' }).click();

    await expect(preguntas.locator('[data-lazy-content]')).toContainText('Pregunta enviada');
    await expect(preguntas.locator('[data-lazy-content]')).toContainText(texto);
    // Sigue en el feed: el form se resolvió por fetch, no por navegación.
    await expect(page).toHaveURL(/\/feed\?.*distance=1/);
  });

  test('las reseñas se cargan al abrir la sección', async ({ page }) => {
    const card = await cardCerca(page);
    const resenias = card.locator('details[data-lazy-section]').nth(1);
    const cuerpo = resenias.locator('[data-lazy-content]');
    await expect(cuerpo).toContainText('Tocá para cargar');

    await resenias.locator('summary').click();
    await expect(cuerpo).not.toContainText('Tocá para cargar');
    // Las experiencias sembradas no tienen reseñas: el fragmento trae el vacío.
    await expect(cuerpo).toContainText('no hay reseñas');
  });

  test('el CTA lleva al detalle con la fecha y los cupos elegidos', async ({ page }) => {
    const card = await cardCerca(page);

    await card.locator('[data-dates-toggle]').click();
    const segunda = card.locator('[data-date-option]').nth(1);
    const instanceId = (await segunda.getAttribute('data-instance-id'))!;
    await segunda.click();

    await card.locator('input[name="attendees"]').fill('2');
    await card.getByRole('button', { name: 'Reservar ahora' }).click();

    await expect(page).toHaveURL(
      new RegExp(`/experiences/\\d+\\?instanceId=${instanceId}&attendees=2$`),
    );
    // Nada de pantalla intermedia: el detalle es el último paso antes de Stripe.
    await expect(page).not.toHaveURL(/\/checkout/);

    // El detalle arranca con esa fecha seleccionada y esos cupos cargados.
    const fechas = page.locator('#fechas');
    await expect(fechas).toBeVisible();
    await expect(fechas.locator('.ring-2 input[name="attendees"]')).toHaveValue('2');
  });
});
