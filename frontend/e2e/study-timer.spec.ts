import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

// E09 T3 (CA3)
test('recarregar a página com sessão ativa reabre o timer no tempo certo', async ({ page }) => {
  await entrarComSessaoFalsa(page);

  // Sessão iniciada há 2 minutos; a API calcula o tempo decorrido, como o backend real
  const inicio = Date.now() - 2 * 60 * 1000;
  await page.route(`${API}/study/sessions/active`, (route) =>
    route.fulfill({
      json: {
        id: 's1', subjectId: 'm1', subjectName: 'Java', lessonId: null, type: 'LESSON', status: 'RUNNING',
        startedAt: new Date(inicio).toISOString(), endedAt: null, pausedSeconds: 0, pausedAt: null,
        elapsedSeconds: Math.floor((Date.now() - inicio) / 1000), plannedMinutes: 50,
      },
    }),
  );
  await page.route(`${API}/study/today`, (route) =>
    route.fulfill({ json: { date: '2026-10-01', reviews: [], lessons: [] } }),
  );

  await page.goto('/estudos');
  await page.reload();

  const relogio = page.locator('.timer__tempo');
  await expect(relogio).toBeVisible();
  const [min, seg] = (await relogio.innerText()).split(':').map(Number);
  const restante = min * 60 + seg;
  // 50 min sugeridos menos os 2 já estudados, com folga para o tempo do teste
  expect(restante).toBeGreaterThanOrEqual(47 * 60 + 50);
  expect(restante).toBeLessThanOrEqual(48 * 60);
  await expect(page.getByText('Java')).toBeVisible();
});
