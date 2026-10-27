/**
 * Flujo de la propuesta con IA (spec 2026-10-26-ai-proposal-design.md §6).
 *
 * POR QUE EXISTE: la suite Java (MockMvc) prueba que cada endpoint responde
 * bien a un request dado, pero no ve el navegador armando los requests: el
 * encadenado gate de intereses → pantalla 3 → PRG con ?draft= → prefill del
 * form, el click de Saltear y el link "Crear con IA" son todos navegados por
 * el cliente. Con el stub (AI_CHAT_PROVIDER=none) generar es instantaneo y
 * sin red, asi que el caso corre siempre igual.
 *
 * Un worker y sin paralelo ya los garantiza playwright.config.ts; ademas los
 * tests comparten base H2.
 */
import { expect, test, request, type APIRequestContext, type Locator } from '@playwright/test';
import type { Page } from '@playwright/test';
import { BASE_URL, DEMO_PASSWORD, USER, login } from './helpers';

/** Spring rota el CSRF al autenticar: se lee del form de la pagina GET. */
function extractCsrf(html: string): string {
  const match = html.match(/name="_csrf"[^>]*value="([^"]+)"/);
  expect(match, 'no se encontro el input _csrf en el HTML').not.toBeNull();
  return match![1];
}

/**
 * Registra un usuario nuevo por la API real (mismos campos exactos que
 * templates/auth/register.html). La contraseña es la misma que usan los
 * tests Java de onboarding, asi que `login()` de helpers sirve tal cual.
 */
async function registrarUsuario(api: APIRequestContext): Promise<string> {
  const page = await api.get('/register');
  expect(page.status(), 'GET /register').toBe(200);
  const csrf = extractCsrf(await page.text());

  const username = `e2e-prop-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  const response = await api.post('/register', {
    maxRedirects: 0,
    form: {
      _csrf: csrf,
      username,
      email: `${username}@e2e.test`,
      password: DEMO_PASSWORD,
      birthDate: '1995-01-01',
      country: 'México',
      currencyCode: 'MXN',
    },
  });
  // 302 => creado y redirigido; 200 significaria volvio al form con errores.
  expect(response.status(), `register de ${username}: ${(await response.text()).slice(0, 400)}`)
    .toBe(302);
  return username;
}

/**
 * Onboarding de intereses por UI, como lo haria una persona: abrir el sheet,
 * entrar a un rubro, tildar el primer subrubro, cerrar y Continuar; en el
 * paso 2 los niveles vienen con default y solo hace falta "Listo, a explorar".
 *
 * Al terminar el servidor manda a /feed, que el gate 2 redirige a la
 * pantalla 3: el caller espera aterrizar en /onboarding/propuesta.
 */
async function completarIntereses(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/onboarding\/intereses/);

  // El boton esta `hidden` hasta que interest-picker.js lo desoculta.
  await page.click('#interest-open');
  await page.locator('.interest-rubro[data-rubro="GASTRONOMIA"]').click();
  await page.locator('.interest-subpanel:not([hidden]) .interest-option').first().click();
  // "Cerrar" solo vive en el header del panel de rubros (ahora hidden): el
  // <dialog> nativo se cierra con Escape, igual que con el boton.
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'Continuar' }).click();

  await expect(page.getByRole('button', { name: 'Listo, a explorar' })).toBeVisible();
  await page.getByRole('button', { name: 'Listo, a explorar' }).click();
}

test('onboarding-redirige-a-la-propuesta', async ({ page }) => {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    const username = await registrarUsuario(api);

    await login(page, username);
    // El login cae en /feed y el gate de intereses manda al paso 1.
    await completarIntereses(page);

    await expect(page).toHaveURL(/\/onboarding\/propuesta/);
    await expect(page.getByRole('heading', { name: 'Tu propuesta de experiencia' })).toBeVisible();
  } finally {
    await api.dispose();
  }
});

test('generar-y-crear-prellena-el-form', async ({ page }) => {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    const username = await registrarUsuario(api);
    await login(page, username);
    await completarIntereses(page);
    await expect(page).toHaveURL(/\/onboarding\/propuesta/);

    // Stub: instantaneo y sin red. El PRG devuelve la pantalla con ?draft={id}.
    await page.getByRole('button', { name: 'Generar propuesta' }).click();
    await expect(page).toHaveURL(/\/onboarding\/propuesta\?next=feed&draft=\d+/);

    // Edita el contenido en la misma pantalla (lo logístico se completa despues).
    const nombre = `Nombre editado e2e-${Date.now()}`;
    const descripcion = 'Descripción editada desde el navegador';
    await page.fill('input[name="name"]', nombre);
    await page.fill('textarea[name="description"]', descripcion);

    await page.getByRole('button', { name: 'Crear experiencia' }).click();
    await expect(page).toHaveURL(/\/experiences\/new\?draft=\d+/);

    // El form viene prellenado con lo que se acaba de editar.
    await expect(page.locator('input[name="name"]')).toHaveValue(nombre);
    await expect(page.locator('textarea[name="description"]')).toHaveValue(descripcion);
  } finally {
    await api.dispose();
  }
});

test('saltear-no-vuelve-a-aparecer', async ({ page }) => {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    const username = await registrarUsuario(api);
    await login(page, username);
    await completarIntereses(page);
    await expect(page).toHaveURL(/\/onboarding\/propuesta/);

    await page.getByRole('button', { name: 'Saltear' }).click();
    await expect(page).toHaveURL(/\/feed$/);

    // Con el flag puesto, recargar /feed ya no redirige a la pantalla 3.
    await page.reload();
    await expect(page).toHaveURL(/\/feed$/);
  } finally {
    await api.dispose();
  }
});

/**
 * Los estilos del form de propuesta viven en app.css, pero el preflight del
 * Tailwind CDN se inyecta DESPUÉS y empata en especificidad: sin regla que
 * gane el empate, textarea y select quedan con padding:0 y el control se
 * achata. Se mide en el navegador porque es exactamente el bug que se vio.
 */
function estilo(el: Locator, prop: 'paddingTop' | 'marginTop' | 'paddingLeft'): Promise<number> {
  return el.evaluate((node, p) => parseFloat(getComputedStyle(node)[p]), prop);
}

test('tarjeta-sin-categoria-ni-subtipo-y-subtipo-derivado', async ({ page }) => {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    const username = await registrarUsuario(api);
    await login(page, username);
    await completarIntereses(page);
    await expect(page).toHaveURL(/\/onboarding\/propuesta/);

    await page.getByRole('button', { name: 'Generar propuesta' }).click();
    await expect(page).toHaveURL(/\/onboarding\/propuesta\?next=feed&draft=\d+/);

    // Categoría y subtipo ya se eligieron en el paso anterior: la tarjeta no
    // los repite. La categoría viaja como hidden (se cambia en el form final).
    await expect(page.locator('#proposal-crear-form select')).toHaveCount(0);
    const categoria = page.locator('#proposal-crear-form input[name="category"]');
    await expect(categoria).toHaveAttribute('type', 'hidden');
    await expect(categoria).not.toHaveValue('');

    // Los campos respiran: el label se despegue del control y el texto del
    // textarea no toca el borde (padding del preflight ganado por app.css).
    const nombre = page.locator('#proposal-crear-form input[type="text"]').first();
    expect(await estilo(nombre, 'marginTop'), 'label pegado al input').toBeGreaterThanOrEqual(10);
    const descripcion = page.locator('#proposal-crear-form textarea').first();
    expect(await estilo(descripcion, 'paddingTop'), 'textarea sin padding').toBeGreaterThan(0);
    await page.screenshot({ path: 'test-results/ui-review/ui-tarjeta-sin-selects.png', fullPage: true });

    await page.getByRole('button', { name: 'Crear experiencia' }).click();
    await expect(page).toHaveURL(/\/experiences\/new\?draft=\d+&subtipo=\d+/);

    // El subtipo derivado del interés llega al form final como hidden…
    await expect(page.locator('input[name="subtipo"]')).toHaveValue(/\d+/);
    // …y el select del form final también tiene su padding (misma regla CSS).
    const tipo = page.locator('select[name="category"]');
    expect(await estilo(tipo, 'paddingLeft'), 'select sin padding').toBeGreaterThan(0);
    await page.screenshot({ path: 'test-results/ui-review/ui-form-con-subtipo.png', fullPage: true });
  } finally {
    await api.dispose();
  }
});

test('entry-desde-crear-experiencia', async ({ page }) => {
  // demo_user ya paso los onnings (DataSeeder), asi que entra directo al feed.
  await login(page, USER);

  await page.goto('/experiences/new');
  await page.getByRole('link', { name: 'Crear con IA' }).click();
  await expect(page).toHaveURL(/\/onboarding\/propuesta\?next=new/);

  // Saltear conserva el destino whitelistado: vuelve al form, no al feed.
  await page.getByRole('button', { name: 'Saltear' }).click();
  await expect(page).toHaveURL(/\/experiences\/new$/);
});