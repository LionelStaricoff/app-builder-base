/**
 * Guardia contra el preflight del Tailwind CDN.
 *
 * POR QUE EXISTE: layout.html carga app.css y DESPUÉS el script del CDN, que
 * inyecta su preflight (button,input,optgroup,select,textarea{padding:0;
 * font-size:100%;color:inherit}) con la misma especificidad que las reglas de
 * elemento de app.css: el empate lo gana el que viene después, o sea el CDN.
 * En marzo-2026 eso dejó los textarea y select de la propuesta con padding 0
 * y el control achatado. Los elementos con clase (.form-input, .btn-*) o con
 * selector de atributo (input[type=text]) nunca se ven afectados, porque ganan
 * en especificidad: por eso la auditoría sólo flaggea controles SIN clase.
 *
 * El test recorre las pantallas con formulario y títulos, mide estilos
 * computados en el navegador real y falla ante cualquier control pisado. La
 * corrección es siempre la misma: regla con selector más específico en
 * app.css (ver body textarea, body select).
 */
import { expect, test, request } from '@playwright/test';
import type { Page } from '@playwright/test';
import { BASE_URL, USER, login, loginViaApi, createExperienceViaApi, SEED } from './helpers';

type Hallazgo = { page: string; el: string; issues: string[] };

/** Mide la página actual. Sólo inspecciona lo que el preflight puede pisar. */
const auditar = (): Hallazgo[] => {
  const out: Hallazgo[] = [];
  const page = location.pathname;
  const cls = (el: Element) =>
    (typeof el.className === 'string' ? el.className : '').trim();
  const tag = (el: Element) =>
    el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.hasAttribute('name') ? '[' + el.getAttribute('name') + ']' : '');

  // Los file inputs (avatar, fotos) viven ocultos tras su dropzone/botón
  // custom: su padding nativo no se ve, no son auditables por estilo.
  for (const el of document.querySelectorAll(
    'select, textarea, input:not([type=hidden]):not([type=radio]):not([type=checkbox]):not([type=file])',
  )) {
    if (cls(el)) continue; // una clase siempre gana al preflight: no hay nada que verificar
    const cs = getComputedStyle(el);
    const issues: string[] = [];
    if (cs.paddingLeft === '0px' || cs.paddingTop === '0px') issues.push('padding:0');
    if (parseFloat(cs.fontSize) !== 16) issues.push('font-size ' + cs.fontSize);
    if (el.tagName !== 'INPUT' && cs.borderRadius === '0px') issues.push('border-radius:0');
    if (el.tagName === 'INPUT' && !['text', 'email', 'password', 'number', 'date', 'time', ''].includes(el.type)) {
      issues.push('input sin tipo esperado: ' + el.type);
    }
    if (issues.length) out.push({ page, el: tag(el), issues });
  }

  // h1-h3 sin clase: el preflight los baja a font-size:inherit (16px = texto
  // normal). Las clases (.page-title, .section-title, .auth-card h1) ganan.
  for (const el of document.querySelectorAll('h1, h2, h3')) {
    if (cls(el)) continue;
    if (parseFloat(getComputedStyle(el).fontSize) === 16) {
      out.push({ page, el: el.tagName.toLowerCase(), issues: ['título sin clase al tamaño del texto'] });
    }
  }

  // Botones sin clase: el preflight les anula padding y border-radius.
  for (const el of document.querySelectorAll('button')) {
    if (cls(el)) continue;
    const cs = getComputedStyle(el);
    if (cs.paddingLeft === '0px' || cs.borderRadius === '0px') {
      out.push({
        page,
        el: 'button' + (el.id ? '#' + el.id : '') + ' «' + (el.textContent || '').trim().slice(0, 18) + '»',
        issues: ['sin clase: padding ' + cs.paddingLeft + ', radius ' + cs.borderRadius],
      });
    }
  }
  return out;
};

async function auditarPagina(page: Page, url: string): Promise<Hallazgo[]> {
  await page.goto(url);
  await page.waitForLoadState('networkidle');
  return page.evaluate(auditar);
}

test('ninguna pantalla queda pisada por el preflight de Tailwind', async ({ page }) => {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    // Una experiencia propia para poder auditar el detalle (textareas de
    // preguntas/respuestas viven ahí).
    await login(page, USER);
    const csrf = await loginViaApi(api, USER);
    await createExperienceViaApi(api, csrf, SEED[0]).catch(() => {
      /* puede ya existir de una corrida anterior: el detalle se busca igual */
    });

    const paginas = [
      '/feed',
      '/favorites',
      '/settings',
      '/experiences/new',
      '/reservations',
      '/notifications',
      '/chats',
      '/my-experiences',
      '/register',
    ];

    const hallazgos: Hallazgo[] = [];
    for (const url of paginas) {
      hallazgos.push(...(await auditarPagina(page, url)));
    }

    // Detalle: la primera card del feed lleva a una experiencia real.
    await page.goto('/feed');
    const href = await page.locator('a[href^="/experiences/"]').first().getAttribute('href');
    if (href) {
      hallazgos.push(...(await auditarPagina(page, href)));
    }
    // El detalle (textareas de preguntas) es parte de la auditoría: si el
    // feed no tuviera experiencia, el test estaría mintiendo de "auditado".
    expect(href, 'el feed no muestra ninguna experiencia: detalle sin auditar').not.toBeNull();

    // Revisión visual de las tres pantallas que dispararon este hallazgo.
    for (const url of ['/feed', '/favorites', '/settings']) {
      await page.goto(url);
      await page.waitForLoadState('networkidle');
      await page.screenshot({
        path: `test-results/ui-review/auditoria-${url.slice(1)}.png`,
        fullPage: true,
      });
    }

    expect(hallazgos, 'controles pisados por el preflight de Tailwind').toEqual([]);
  } finally {
    await api.dispose();
  }
});
