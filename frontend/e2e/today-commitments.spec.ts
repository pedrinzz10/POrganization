import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

// C10 T2 (CA2)
test('criar pela criação rápida na tela Hoje mostra o item sem recarregar a página', async ({ page }) => {
  await entrarComSessaoFalsa(page);

  // API em memória: o POST guarda, o GET /today devolve o que foi guardado
  const hoje = new Date();
  const data = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
  const salvos: Record<string, unknown>[] = [];
  await page.route(`${API}/today`, (route) =>
    route.fulfill({ json: { date: data, timezone: 'America/Sao_Paulo', commitments: salvos, studies: { reviews: [], lessons: [] } } }),
  );
  await page.route(`${API}/commitments`, async (route) => {
    const body = route.request().postDataJSON();
    const id = `id-${salvos.length + 1}`;
    salvos.push({
      commitmentId: id, occurrenceDate: body.date, title: body.title, startTime: body.startTime ?? null,
      endTime: null, allDay: !body.startTime, done: false, recurring: false, description: null, location: null,
    });
    await route.fulfill({ status: 201, json: { id, ...body, allDay: !body.startTime, done: false } });
  });

  await page.goto('/hoje');
  await expect(page.getByText('Nenhum compromisso')).toBeVisible();
  await page.evaluate(() => ((window as unknown as { semRecarregar: boolean }).semRecarregar = true));

  await page.getByLabel('Novo compromisso').fill('Reunião');
  await page.getByLabel('Hora').fill('15:00');
  await page.getByLabel('Novo compromisso').press('Enter');

  const secao = page.getByRole('region', { name: 'Compromissos de hoje' });
  await expect(secao.getByText('Reunião')).toBeVisible();
  await expect(secao.getByText('15:00')).toBeVisible();
  // a marca continua: a página não foi recarregada
  expect(await page.evaluate(() => (window as unknown as { semRecarregar?: boolean }).semRecarregar)).toBe(true);
});
