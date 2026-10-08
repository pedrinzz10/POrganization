import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

// B14 T5 (CA3)
test('apagar finanças pede para digitar APAGAR e chama a API', async ({ page }) => {
  await entrarComSessaoFalsa(page);
  await page.route(`${API}/settings`, (route) =>
    route.fulfill({
      json: {
        email: null,
        timezone: 'America/Sao_Paulo',
        channels: [],
        defaultReminderMinutes: null,
        digestTime: null,
        scheduledNoticeTime: null,
      },
    }),
  );
  await page.route(`${API}/integrations/google`, (route) =>
    route.fulfill({
      json: {
        available: false,
        connected: false,
        email: null,
        calendarId: null,
        connectedAt: null,
      },
    }),
  );
  const apagadas: string[] = [];
  await page.route(`${API}/data/*`, (route) => {
    apagadas.push(`${route.request().method()} ${new URL(route.request().url()).pathname}`);
    return route.fulfill({ status: 204 });
  });

  await page.goto('/configuracoes');
  const bloco = page.getByRole('region', { name: 'Apagar dados' });
  await bloco.getByRole('button', { name: 'Apagar finanças' }).click();

  const dialogo = page.getByRole('dialog', { name: 'Apagar finanças?' });
  const confirmar = dialogo.getByRole('button', { name: 'Apagar' });
  await expect(confirmar).toBeDisabled();
  await dialogo.getByLabel('Digite APAGAR para confirmar').fill('APAGAR');
  await confirmar.click();

  await expect(page.getByText('Finanças: dados apagados.')).toBeVisible();
  expect(apagadas).toEqual(['DELETE /api/data/finance']);
});

// B15 T2 (CA1)
test('cada seção tem o botão "Apagar dados" ao lado do título', async ({ page }) => {
  await entrarComSessaoFalsa(page);
  await page.route(`${API}/**`, (route) => route.fulfill({ json: [] }));
  const apagadas: string[] = [];
  await page.route(`${API}/data/*`, (route) => {
    apagadas.push(new URL(route.request().url()).pathname);
    return route.fulfill({ status: 204 });
  });

  for (const [url, titulo] of [
    ['/compromissos', 'Compromissos'],
    ['/tarefas', 'Tarefas'],
    ['/estudos', 'Estudos'],
    ['/financas', 'Finanças'],
  ]) {
    await page.goto(url);
    await expect(page.getByRole('heading', { level: 1, name: titulo })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Apagar dados' })).toBeVisible();
  }

  await page.getByRole('button', { name: 'Apagar dados' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Apagar finanças?' });
  await dialogo.getByLabel('Digite APAGAR para confirmar').fill('APAGAR');
  await dialogo.getByRole('button', { name: 'Apagar' }).click();
  await expect(page.getByText('Finanças: dados apagados.')).toBeVisible();
  expect(apagadas).toEqual(['/api/data/finance']);
  await expect(page).toHaveURL(/\/financas/);
});
