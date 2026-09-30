import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api/finance';
const MESES = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];

test('o resumo desenha os dois gráficos sem erro no console', async ({ page }) => {
  const erros: string[] = [];
  page.on('console', (msg) => msg.type() === 'error' && erros.push(msg.text()));
  page.on('pageerror', (erro) => erros.push(erro.message));
  await entrarComSessaoFalsa(page);
  await page.route(`${API}/dashboard?**`, (route) =>
    route.fulfill({
      json: {
        month: '2026-10', totalBalance: '4550.00', accounts: [], income: '5000.00', expense: '950.00', net: '4050.00',
        expenseByCategory: [
          { categoryId: 'a', name: 'Alimentação', color: '#F57C00', total: '750.00' },
          { categoryId: 't', name: 'Transporte', color: '#1976D2', total: '200.00' },
        ],
        cards: [], budgetAlerts: [], goals: [], receivable: '0.00', payable: '0.00', forecast: '6350.00',
        lastSixMonths: MESES.map((month, i) => ({ month, income: i === 5 ? '5000.00' : '0.00', expense: i === 5 ? '950.00' : '100.00' })),
      },
    }),
  );

  await page.goto('/financas?month=2026-10');

  await expect(page.getByRole('tab', { name: 'Resumo' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByText('R$ 4.550,00')).toBeVisible();
  const graficos = page.locator('canvas');
  await expect(graficos).toHaveCount(2);
  for (const canvas of await graficos.all()) {
    const box = await canvas.boundingBox();
    expect(box!.height).toBeGreaterThan(50);
  }
  expect(erros).toEqual([]);
});
