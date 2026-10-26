/**
 * Smoke E2E del feed.
 *
 * POR QUE EXISTE: la suite Java (MockMvc) le pasa una URL al servidor y mira el
 * HTML. Nunca ejecuta JavaScript ni el algoritmo de envio de formularios del
 * navegador, asi que no puede ver bugs como el de loader.js (deshabilitar el
 * boton que dispara el submit => el navegador descarta su name/value y manda
 * /feed? vacio). Estos tres casos cubren justo ese punto ciego: la UI armando
 * el request.
 *
 * La app se levanta sola (webServer) contra H2 en memoria, y globalSetup siembra
 * 3 experiencias por la API real (multipart + CSRF), a 0 / 2.6 / 9.1 km del
 * demo_user.
 */
import { defineConfig, devices } from '@playwright/test';

/** Puerto distinto al de desarrollo (8080) para no chocar con una app abierta. */
const PORT = 8081;

export default defineConfig({
  testDir: './tests',
  globalSetup: './tests/global-setup',
  // Un worker: comparten la misma base y el caso de favoritos la modifica.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${PORT}`,
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // fork=false: la app corre en la JVM de Maven, asi matar el proceso la mata
    // (con fork por defecto queda un hijo huerfano en Windows).
    command: 'mvnw.cmd spring-boot:run -Dspring-boot.run.fork=false',
    cwd: '..',
    url: `http://localhost:${PORT}/login`,
    reuseExistingServer: true,
    timeout: 240_000,
    env: {
      // Se propaga process.env entero a proposito: segun la version, Playwright
      // reemplaza o mezcla, y sin PATH/JAVA_HOME Maven no arranca.
      ...process.env,
      SERVER_PORT: String(PORT),
      // H2 en memoria: cada corrida arranca con la base limpia y Flyway aplica
      // las migraciones. Los env var le ganan al .env del repo (Postgres remota).
      SPRING_DATASOURCE_URL: 'jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
      SPRING_DATASOURCE_USERNAME: 'sa',
      SPRING_DATASOURCE_PASSWORD: '',
      IDENTITY_VERIFICATION_ENABLED: 'false',
    },
  },
});
