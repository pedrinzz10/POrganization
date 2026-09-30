import { defineConfig, devices } from '@playwright/test';

// Testes e2e: sobem o app com `npm start` (ou reaproveitam um já rodando) e usam o Chromium.
export default defineConfig({
  testDir: './e2e',
  // smoke-prod.spec.ts roda contra a Vercel (B11), não no ciclo local
  testIgnore: /smoke-prod\.spec\.ts/,
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm start -- --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
