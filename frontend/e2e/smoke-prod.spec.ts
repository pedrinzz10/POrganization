import { expect, test } from '@playwright/test';

// B11: roda contra o deploy real com `npm run e2e:prod` (ver playwright.prod.config.ts).
const BASE_URL = process.env['PROD_BASE_URL'];
const API_URL = process.env['PROD_API_URL'];
const EMAIL = process.env['E2E_USER_EMAIL'];
const PASSWORD = process.env['E2E_USER_PASSWORD'];

test.describe('smoke de produção', () => {
  test.skip(!BASE_URL, 'defina PROD_BASE_URL para rodar contra o deploy');

  // B11 T2 (CA2): o rewrite de SPA da Vercel entrega o app em qualquer rota
  test('abrir /financas direto carrega o app, não um 404', async ({ page }) => {
    const resposta = await page.goto('/financas');
    expect(resposta?.status()).toBe(200);
    await expect(page).toHaveTitle(/POrganization/);
    // Sem sessão, o guard leva ao login: prova que o Angular rodou
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  });

  // B11 T3 (CA3): login real no Supabase e chamada autenticada à API no Render
  test('login em produção chega em /hoje e /api/me responde 200', async ({ page }) => {
    test.skip(!API_URL || !EMAIL || !PASSWORD, 'defina PROD_API_URL, E2E_USER_EMAIL e E2E_USER_PASSWORD');

    await page.goto('/login');
    await page.getByLabel('E-mail').fill(EMAIL!);
    await page.getByLabel('Senha').fill(PASSWORD!);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page).toHaveURL(/\/hoje$/);

    // Usa o mesmo token que o app guardou no localStorage
    const token = await page.evaluate(() => {
      const chave = Object.keys(localStorage).find((k) => k.startsWith('sb-') && k.endsWith('-auth-token'));
      return chave ? (JSON.parse(localStorage.getItem(chave)!) as { access_token: string }).access_token : null;
    });
    expect(token).toBeTruthy();

    const me = await page.request.get(`${API_URL}/me`, { headers: { Authorization: `Bearer ${token}` } });
    expect(me.status()).toBe(200);
    expect((await me.json()).email).toBe(EMAIL);
  });
});
