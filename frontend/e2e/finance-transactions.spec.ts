import { expect, Page, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api/finance';

const CATEGORIAS = [
  { id: 'cat-lazer', name: 'Lazer', kind: 'EXPENSE', color: '#8E24AA', icon: 'celebration' },
  { id: 'cat-mercado', name: 'Alimentação', kind: 'EXPENSE', color: '#F57C00', icon: 'restaurant' },
  { id: 'cat-salario', name: 'Salário', kind: 'INCOME', color: '#43A047', icon: 'payments' },
];

interface Lancamento {
  id: string;
  type: string;
  amount: string;
  date: string;
  description: string | null;
  accountId: string;
  categoryId: string;
  paid: boolean;
}

/**
 * API de finanças de mentira, com estado: lançar um gasto muda o extrato e o saldo da conta,
 * como o backend faz. Guarda as consultas do extrato para conferir os filtros enviados.
 */
async function apiFalsa(page: Page) {
  const saldoInicial = 100000; // centavos
  const lancamentos: Lancamento[] = [];
  const consultas: URL[] = [];
  const reais = (centavos: number) => (centavos / 100).toFixed(2);
  const saldo = () =>
    lancamentos.reduce((s, t) => s + (t.type === 'INCOME' ? 1 : -1) * Math.round(Number(t.amount) * 100), saldoInicial);

  await page.route(`${API}/accounts**`, (route) =>
    route.fulfill({
      json: [{ id: 'conta-1', name: 'Corrente', type: 'CHECKING', initialBalance: '1000.00', balance: reais(saldo()), archived: false }],
    }),
  );
  await page.route(`${API}/categories`, (route) => route.fulfill({ json: CATEGORIAS }));
  await page.route(`${API}/tags`, (route) => route.fulfill({ json: [] }));
  await page.route(`${API}/summary**`, (route) => {
    const gasto = lancamentos.filter((t) => t.type === 'EXPENSE').reduce((s, t) => s + Number(t.amount), 0);
    route.fulfill({ json: { month: '2026-10', income: '0.00', expense: gasto.toFixed(2), net: (-gasto).toFixed(2) } });
  });
  await page.route(`${API}/transactions**`, async (route) => {
    const request = route.request();
    if (request.method() === 'POST') {
      const corpo = request.postDataJSON();
      const novo = { ...corpo, id: `t${lancamentos.length + 1}` };
      lancamentos.push(novo);
      await route.fulfill({ status: 201, json: resposta(novo) });
      return;
    }
    const url = new URL(request.url());
    consultas.push(url);
    const categoria = url.searchParams.get('categoryId');
    await route.fulfill({ json: lancamentos.filter((t) => !categoria || t.categoryId === categoria).map(resposta) });
  });

  function resposta(t: Lancamento) {
    const categoria = CATEGORIAS.find((c) => c.id === t.categoryId);
    return {
      ...t, accountName: 'Corrente', categoryName: categoria?.name ?? null, tags: [], cardStatementId: null, purchaseId: null,
      installmentNumber: null, installmentCount: null, transferGroupId: null, transferDirection: null, recurringId: null,
    };
  }

  return { consultas };
}

test.beforeEach(async ({ page }) => {
  await entrarComSessaoFalsa(page);
});

// F12 T2 (CA2)
test('filtro de categoria vai para a URL e continua depois de recarregar', async ({ page }) => {
  const { consultas } = await apiFalsa(page);
  await page.goto('/financas/extrato?month=2026-10');

  await page.getByRole('combobox', { name: 'Categoria' }).last().click();
  await page.getByRole('option', { name: 'Lazer' }).click();

  await expect(page).toHaveURL(/[?&]category=cat-lazer/);
  await expect(page).toHaveURL(/[?&]month=2026-10/);

  await page.reload();
  await expect(page.locator('.filtros').getByRole('combobox', { name: 'Categoria' })).toContainText('Lazer');
  await expect.poll(() => consultas.at(-1)?.searchParams.get('categoryId')).toBe('cat-lazer');
  expect(consultas.at(-1)?.searchParams.get('month')).toBe('2026-10');
});

// F12 T3 (CA3)
test('lançar um gasto mostra a linha e atualiza o saldo sem recarregar', async ({ page }) => {
  await apiFalsa(page);
  await page.goto('/financas/extrato?month=2026-10');
  const saldo = page.locator('.saldo[data-conta="Corrente"]');
  await expect(saldo).toContainText('R$ 1.000,00');

  const form = page.getByRole('region', { name: 'Novo lançamento' });
  await form.getByLabel('Valor').pressSequentially('5000');
  await expect(form.getByLabel('Valor')).toHaveValue('R$ 50,00');
  await form.getByLabel('Descrição').fill('Cinema');
  await form.getByRole('combobox', { name: 'Categoria' }).click();
  await page.getByRole('option', { name: 'Lazer' }).click();
  await form.getByLabel('Data').fill('2026-10-10');
  await form.getByRole('button', { name: 'Lançar' }).click();

  const extrato = page.getByRole('list', { name: 'Lançamentos do mês' });
  await expect(extrato.getByText('Cinema')).toBeVisible();
  await expect(extrato).toContainText('-R$ 50,00');
  await expect(saldo).toContainText('R$ 950,00');
});
