import { test, expect, request } from '@playwright/test';
import {
  BASE_URL,
  SEED,
  createExperienceViaApi,
  login,
  loginViaApi,
} from './helpers';

/**
 * Modal de confirmación (fragments/modal::confirm + confirm-modal.js).
 *
 * POR QUE EXISTE: los dos confirm() nativos de la app (Eliminar experiencia
 * y Cancelar fecha) eran bloqueantes, no estilizables y estaban fuera del
 * sistema visual. Ahora son un <dialog> abierto por data-modal-open.
 * La suite Java no ejecuta JavaScript: nunca podria ver que el modal se abre,
 * que Esc lo cierra ni que el form postea con CSRF. Por eso va acá.
 */

/** Crea y borra una experiencia propia para no tocar las 3 sembradas. */
async function createDisposableExperience(name: string): Promise<void> {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    const csrf = await loginViaApi(api, 'demo_creator');
    await createExperienceViaApi(api, csrf, {
      name,
      description: 'Experiencia desechable para el test del modal.',
      lat: SEED[0].lat,
      lng: SEED[0].lng,
    });
  } finally {
    await api.dispose();
  }
}

test.describe('modal de confirmación (reemplaza a confirm())', () => {
  test('abre, se cierra con Cancelar, Esc y clic afuera, y devuelve el foco', async ({ page }) => {
    await login(page, 'demo_creator');
    await page.goto('/my-experiences');

    const trigger = page.getByRole('button', { name: 'Eliminar', exact: true }).first();
    await trigger.click();

    const dialog = page.locator(`#${await trigger.getAttribute('data-modal-open')}`);
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('¿Eliminar esta experiencia?')).toBeVisible();

    // El form vive adentro del fragment y tiene que postear con CSRF.
    const form = dialog.locator('form');
    await expect(form).toHaveAttribute('method', 'post');
    await expect(form).toHaveAttribute('action', /\/experiences\/\d+\/delete$/);
    await expect(form.locator('input[name="_csrf"]')).toHaveCount(1);

    // El foco arranca en Cancelar: un Enter doble no debe borrar sin leer.
    await expect(dialog.locator('[data-modal-close]')).toBeFocused();

    // Esc: lo cierra el <dialog> nativo y el foco vuelve al disparador.
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();

    // Botón Cancelar.
    await trigger.click();
    await dialog.locator('[data-modal-close]').click();
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();

    // Clic en el backdrop (fuera de la caja): cierra y no navega.
    await trigger.click();
    await expect(dialog).toBeVisible();
    const box = await dialog.boundingBox();
    expect(box, 'el dialog abierto debe tener caja visible').not.toBeNull();
    expect(box!.y, 'la caja debe estar centrada, no pegada arriba').toBeGreaterThan(10);
    await page.mouse.click(10, 10);
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
    await expect(page).toHaveURL(/\/my-experiences/);
  });

  test('Confirmar postea de verdad y borra la experiencia', async ({ page }) => {
    const NAME = 'E2E Borrar';
    await createDisposableExperience(NAME);

    await login(page, 'demo_creator');
    await page.goto('/my-experiences');

    const card = page.locator('.card', { hasText: NAME });
    await expect(card).toBeVisible();
    const btn = card.getByRole('button', { name: 'Eliminar', exact: true });
    await btn.click();

    const dialog = page.locator(`#${await btn.getAttribute('data-modal-open')}`);
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Confirmar' }).click();

    // El POST sale desde el form del fragment: si fallara el CSRF o la URL,
    // esto caería en 403 o en la página de error, no en /my-experiences.
    await expect(page).toHaveURL(/\/my-experiences/);
    await expect(page.getByText('Experiencia eliminada')).toBeVisible();
    // Borrado lógico: la card queda con el chip "Cancelada".
    await expect(page.locator('.card', { hasText: NAME }).locator('.chip')).toHaveText('Cancelada');
  });

  test('detalle: el dueño abre el modal de Cancelar fecha', async ({ page }) => {
    await login(page, 'demo_creator');
    await page.goto('/my-experiences');

    await page
      .locator('.card', { hasText: SEED[0].name })
      .locator('.card-head h3 a')
      .click();
    await expect(page).toHaveURL(/\/experiences\/\d+/);

    const trigger = page.getByRole('button', { name: 'Cancelar fecha' });
    await expect(trigger).toBeVisible();
    await trigger.click();

    const dialog = page.locator(`#${await trigger.getAttribute('data-modal-open')}`);
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(/¿Cancelar esta fecha\? Se reembolsa el 100%/)).toBeVisible();
    await expect(dialog.locator('form')).toHaveAttribute('action', /\/instances\/\d+\/cancel$/);
    await expect(dialog.locator('[data-modal-close]')).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
  });
});
