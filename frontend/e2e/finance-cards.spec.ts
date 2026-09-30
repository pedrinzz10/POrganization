import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api/finance';

const CARTAO = { id: 'nubank', name: 'Nubank', creditLimit: '3000.00', closingDay: 5, dueDay: 12, paymentAccountId: 'c1', archived: false };

function fatura(mes: string) {
  return {
    id: `f-${mes}`, cardId: 'nubank', referenceMonth: mes, closingDate: `${mes}-05`, dueDate: `${mes}-12`, status: 'OPEN',
    total: '33.34', creditLimit: '3000.00', availableLimit: '2900.00', paidAt: null, paymentTransactionId: null,
    items: [{ id: `t-${mes}`, date: '2026-10-01', description: 'Loja (1/3)', amount: '33.34', categoryId: 'c', purchaseId: 'p1', installmentNumber: 1, installmentCount: 3 }],
  };
}

test('cartão mostra o limite usado e abre a fatura pela rota /cartoes/:id/faturas/:mes', async ({ page }) => {
  await entrarComSessaoFalsa(page);
  await page.route(`${API}/cards`, (route) => route.fulfill({ json: [CARTAO] }));
  await page.route(`${API}/categories`, (route) => route.fulfill({ json: [] }));
  await page.route(`${API}/cards/nubank/statements?**`, (route) =>
    route.fulfill({ json: fatura(new URL(route.request().url()).searchParams.get('month')!) }),
  );

  await page.goto('/financas/cartoes');
  const cartao = page.getByRole('article', { name: 'Nubank' });
  await expect(cartao).toContainText('Usado R$ 100,00');
  await expect(cartao).toContainText('Disponível R$ 2.900,00');

  await cartao.getByRole('link', { name: /Fatura atual/ }).click();
  await expect(page).toHaveURL(/\/financas\/cartoes\/nubank\/faturas\/\d{4}-\d{2}$/);
  await expect(page.getByRole('list', { name: 'Compras da fatura' })).toContainText('Loja (1/3)');

  await page.goto('/financas/cartoes/nubank/faturas/2026-10');
  await page.getByRole('link', { name: 'Próxima fatura' }).click();
  await expect(page).toHaveURL('/financas/cartoes/nubank/faturas/2026-11');
  await expect(page.getByRole('heading', { level: 2 })).toContainText('novembro de 2026');
  await expect(page.getByRole('tab', { name: 'Cartões' })).toHaveAttribute('aria-selected', 'true');
});
