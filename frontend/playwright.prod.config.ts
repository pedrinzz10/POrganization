import { defineConfig, devices } from '@playwright/test';

// Smoke test contra o deploy real (B11). Não sobe servidor local.
// Uso: PROD_BASE_URL=https://porganization.vercel.app PROD_API_URL=https://porganization-api.onrender.com/api \
//      E2E_USER_EMAIL=... E2E_USER_PASSWORD=... npm run e2e:prod
export default defineConfig({
  testDir: './e2e',
  testMatch: /smoke-prod\.spec\.ts/,
  // O plano gratuito do Render hiberna: a primeira chamada à API pode levar ~1 minuto
  timeout: 120_000,
  retries: 1,
  reporter: 'list',
  use: {
    baseURL: process.env['PROD_BASE_URL'],
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
