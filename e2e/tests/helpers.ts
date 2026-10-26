import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import path from 'node:path';

export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8081';

/** Default de app.demo.password (DEMO_PASSWORD) en application.properties. */
export const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? 'Clave1234!';

export const USER = 'demo_user';
export const CREATOR = 'demo_creator';

/** El demo_user esta en CDMX (19.4326, -99.1332), ver DataSeeder. */
const USER_LAT = 19.4326;
const USER_LNG = -99.1332;

/**
 * Tres experiencias a distancias conocidas del usuario: 0 km, ~2.6 km y ~9.1 km.
 * Con radios de 1/5/10 km se puede verificar el corte exacto sin depender de
 * decimales: 1 => 1, 5 => 2, 10 => 3.
 */
export const SEED = [
  { name: 'E2E Cerca', description: 'A metros del usuario.', lat: USER_LAT, lng: USER_LNG },
  { name: 'E2E Medio', description: 'A poco mas de dos kilometros.', lat: 19.456, lng: USER_LNG },
  { name: 'E2E Lejos', description: 'Cerca del limite del radio grande.', lat: 19.5143, lng: USER_LNG },
];

export const [CERCA, MEDIO, LEJOS] = SEED.map((e) => e.name);

const ICON = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'static', 'icons', 'icon-512.png');

/**
 * Spring rota el token CSRF al autenticar, asi que hay que leerlo de una pagina
 * de formulario DESPUES del login (no del /login previo).
 */
function extractCsrf(html: string): string {
  const match = html.match(/name="_csrf"[^>]*value="([^"]+)"/);
  expect(match, 'no se encontro el input _csrf en el HTML del formulario').not.toBeNull();
  return match![1];
}

export async function loginViaApi(api: APIRequestContext, username: string): Promise<string> {
  const loginPage = await api.get('/login');
  expect(loginPage.status(), 'GET /login').toBe(200);
  const loginCsrf = extractCsrf(await loginPage.text());

  const response = await api.post('/login', {
    form: { username, password: DEMO_PASSWORD, _csrf: loginCsrf },
    maxRedirects: 0,
  });
  expect(response.status(), `login de ${username}`).toBe(302);
  expect(response.headers()['location'], `login de ${username}`).not.toContain('error');

  const formPage = await api.get('/experiences/new');
  expect(formPage.status(), 'GET /experiences/new despues del login').toBe(200);
  return extractCsrf(await formPage.text());
}

function inThirtyDays(): string {
  const date = new Date();
  date.setDate(date.getDate() + 30);
  return date.toISOString().slice(0, 10);
}

/** Crea una experiencia por la API real (multipart + CSRF), no tocando la base. */
export async function createExperienceViaApi(
  api: APIRequestContext,
  csrf: string,
  seed: (typeof SEED)[number],
): Promise<void> {
  const response = await api.post('/experiences', {
    maxRedirects: 0,
    multipart: {
      _csrf: csrf,
      name: seed.name,
      description: seed.description,
      category: 'GASTRONOMIA',
      recurrenceType: 'UNIQUE',
      durationMinutes: '90',
      minCapacity: '1',
      maxCapacity: '10',
      price: '250.00',
      currencyCode: 'MXN',
      'schedules[0].date': inThirtyDays(),
      'schedules[0].startTime': '10:00',
      'schedules[0].endTime': '12:00',
      meetingLat: String(seed.lat),
      meetingLng: String(seed.lng),
      images: {
        name: 'icon-512.png',
        mimeType: 'image/png',
        buffer: readFileSync(ICON),
      },
    },
  });

  // 302 => creada. 200 significa que volvio al formulario con errores de
  // validacion: el body lo dice, y sin el body el fallo es ilegible.
  expect(response.status(), `crear "${seed.name}": ${(await response.text()).slice(0, 400)}`).toBe(302);
  // Tomcat puede mandar el Location absoluto (http://host/experiences/1) o
  // relativo; las dos son validas.
  expect(response.headers()['location'], `crear "${seed.name}"`).toMatch(
    /^(?:https?:\/\/[^/]+)?\/experiences\/\d+$/,
  );
}

/** Login por la UI, como lo haria una persona. */
export async function login(page: Page, username = USER): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/** Titulos de las cards presentes en el DOM, en orden. */
export async function cardTitles(page: Page): Promise<string[]> {
  return (await page.locator('.feed-card-title').allInnerTexts()).map((t) => t.trim());
}

/**
 * Baja por el scroll infinito hasta que no aparezcan mas cards.
 *
 * No se puede usar un timeout fijo: el feed carga de a UNA card (PAGE_SIZE=1) y
 * el IntersectionObserver del cliente solo dispara cuando el centinela cruza el
 * umbral, asi que hay que empujar el scroll y esperar a que el conteo suba de
 * verdad (8s por salto, y si no sube es porque el endpoint devolvio vacio).
 */
async function waitForMoreCards(page: Page, previous: number, timeout = 8_000): Promise<boolean> {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if ((await page.locator('.feed-card').count()) > previous) return true;
    await page.waitForTimeout(100);
  }
  return false;
}

export async function exhaustFeed(page: Page, expected?: number): Promise<void> {
  let count = await page.locator('.feed-card').count();
  for (let i = 0; i < 25; i++) {
    if (expected !== undefined && count >= expected) return;

    await page.mouse.wheel(0, 900);
    let grew = await waitForMoreCards(page, count);

    if (!grew) {
      // El centinela pudo quedar dentro del rootMargin y no volver a cruzar:
      // un scroll explicito hasta el lo fuerza a salir y entrar.
      await page.locator('#feed-sentinel').scrollIntoViewIfNeeded();
      grew = await waitForMoreCards(page, count);
    }
    if (!grew) {
      if (expected !== undefined && count < expected) {
        throw new Error(`el feed se quedo en ${count} cards y se esperaban ${expected}`);
      }
      return;
    }
    count = await page.locator('.feed-card').count();
  }
  throw new Error('el scroll infinito no termino en 25 saltos');
}
