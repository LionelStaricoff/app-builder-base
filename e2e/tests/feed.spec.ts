import { expect, test } from '@playwright/test';
import { CERCA, LEJOS, MEDIO, USER, cardTitles, exhaustFeed, login } from './helpers';

/**
 * Los tres casos que la suite MockMvc no puede cubrir: todo lo que el navegador
 * arma (la URL del submit, los pedidos del scroll infinito, el estado del
 * corazon). Ver el comentario de playwright.config.ts.
 */
test.describe('feed', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, USER);
  });

  test('el chip de distancia arma la URL con el radio y filtra el feed', async ({ page }) => {
    await page.goto('/feed');

    // Regresion de loader.js: el guard de submit deshabilitaba el boton que
    // disparaba el envio, y el navegador arma la lista de campos DESPUES del
    // evento descartando los disabled => viajaba /feed? vacio.
    await page.locator('button.distance-pill[name="distance"][value="5"]').click();

    await expect(page).toHaveURL(/\/feed\?.*distance=5(?:&|$)/);
    await expect(page.getByText('Hasta 5 km')).toBeVisible();

    await exhaustFeed(page, 2);
    expect((await cardTitles(page)).sort()).toEqual([CERCA, MEDIO].sort());
    await expect(page.getByText(LEJOS)).toHaveCount(0);
  });

  test('el scroll infinito mantiene el radio en cada pedido de cards', async ({ page }) => {
    const cardRequests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/feed/cards')) cardRequests.push(request.url());
    });

    await page.goto('/feed?distance=10');
    await exhaustFeed(page, 3);

    await expect.poll(() => cardRequests.length).toBeGreaterThan(0);
    for (const url of cardRequests) {
      expect(url, 'cada fragmento tiene que llevar el radio activo').toContain('distance=10');
    }
    expect((await cardTitles(page)).sort()).toEqual([CERCA, MEDIO, LEJOS].sort());
  });

  test('la lupa abre el buscador y el submit arma /feed?city=', async ({ page }) => {
    await page.goto('/feed');

    // El copy decorativo del hero se fue; tiene que seguir fuera.
    await expect(page.getByText('Explorá los mejores rincones del mundo')).toHaveCount(0);
    await expect(page.getByText('Filtros por categoría')).toHaveCount(0);

    const panel = page.locator('#feed-search');
    await expect(panel).toBeHidden();

    await page.locator('#btn-search').click();
    await expect(panel).toBeVisible();
    const input = panel.locator('input[name="city"]');
    await expect(input).toBeFocused();

    await input.fill('Cartagena');
    await input.press('Enter');
    await expect(page).toHaveURL(/\/feed\?.*city=Cartagena/);

    // Con busqueda activa el servidor la renderiza abierta y la lupa la cierra.
    await expect(panel).toBeVisible();
    await page.locator('#btn-search').click();
    await expect(panel).toBeHidden();
  });

  test('el corazon de favoritos refleja el estado en el feed y en /favorites', async ({ page }) => {
    // Radio de 1 km: queda una sola card, asi la seleccion no depende del orden.
    await page.goto('/feed?distance=1');
    const card = page.locator('.feed-card', { hasText: CERCA });
    await expect(card).toBeVisible();
    await expect(card.locator('form[action$="/add"]')).toHaveCount(1);
    await expect(card.locator('svg.fill-rose-500')).toHaveCount(0);

    await card.locator('form[action*="/favorites/"] button').click();

    // El POST vuelve al Referer (con query incluida) y la card se repinta
    // apuntando a /remove con el corazon relleno.
    await expect(page).toHaveURL(/\/feed\?.*distance=1/);
    const marcada = page.locator('.feed-card', { hasText: CERCA });
    await expect(marcada.locator('form[action$="/remove"]')).toHaveCount(1);
    await expect(marcada.locator('svg.fill-rose-500')).toBeVisible();

    await page.goto('/favorites');
    await expect(page.getByText(CERCA)).toBeVisible();

    // Se desmarca: ademas de probar el toggle, deja la base como estaba para
    // que los tests no se pisen entre corridas.
    await page.goto('/feed?distance=1');
    await page.locator('.feed-card', { hasText: CERCA }).locator('form[action*="/favorites/"] button').click();
    await expect(page.locator('.feed-card', { hasText: CERCA }).locator('form[action$="/add"]')).toHaveCount(1);

    await page.goto('/favorites');
    await expect(page.getByText(CERCA)).toHaveCount(0);
  });
});
