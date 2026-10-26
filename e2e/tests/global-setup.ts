import { request } from '@playwright/test';
import { BASE_URL, CREATOR, SEED, createExperienceViaApi, loginViaApi } from './helpers';

/**
 * Siembra las 3 experiencias antes de los tests. Se hace por la API real
 * (multipart + CSRF) y no por la UI: cargar el formulario tres veces con mapa,
 * imagen y horarios es lento y fragil, y el arrange no es lo que se esta
 * probando. La base es H2 en memoria, asi que cada corrida arranca vacia.
 */
export default async function globalSetup(): Promise<void> {
  const api = await request.newContext({ baseURL: BASE_URL });
  try {
    const csrf = await loginViaApi(api, CREATOR);
    for (const seed of SEED) {
      await createExperienceViaApi(api, csrf, seed);
    }
  } finally {
    await api.dispose();
  }
}
