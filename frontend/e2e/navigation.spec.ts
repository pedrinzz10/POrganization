import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa, USUARIO_TESTE } from './support/session';

const SECOES = [
  { nome: 'Hoje', url: '/hoje' },
  { nome: 'Compromissos', url: '/compromissos' },
  { nome: 'Estudos', url: '/estudos' },
  { nome: 'Finanças', url: '/financas' },
  { nome: 'Estatísticas', url: '/estatisticas' },
  { nome: 'Configurações', url: '/configuracoes' },
];

test.beforeEach(async ({ page }) => {
  await entrarComSessaoFalsa(page);
});

// B09 T1 (CA1)
test('cada item do menu navega para a sua seção e fica destacado', async ({ page }) => {
  await page.goto('/hoje');
  const menu = page.getByRole('navigation', { name: 'Menu principal' });
  await expect(page.getByText(USUARIO_TESTE.email)).toBeVisible();

  for (const secao of SECOES) {
    const link = menu.getByRole('link', { name: secao.nome });
    await link.click();
    await expect(page).toHaveURL(secao.url);
    await expect(page.getByRole('heading', { level: 1, name: secao.nome })).toBeVisible();
    await expect(link).toHaveAttribute('aria-current', 'page');
    await expect(link).toHaveClass(/nav-item--ativo/);
    // Só o item atual fica destacado
    await expect(menu.locator('[aria-current="page"]')).toHaveCount(1);
  }
});

test('no computador o menu fica sempre aberto, sem botão de menu', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/hoje');
  await expect(
    page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Estudos' }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abrir menu' })).toBeHidden();
});

// B09 T2 (CA2)
test('no celular o menu vira gaveta aberta pelo botão', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/hoje');
  const linkEstudos = page
    .getByRole('navigation', { name: 'Menu principal' })
    .getByRole('link', { name: 'Estudos' });

  await expect(linkEstudos).toBeHidden();
  await page.getByRole('button', { name: 'Abrir menu' }).click();
  await expect(linkEstudos).toBeVisible();

  // Escolher uma seção navega e fecha a gaveta
  await linkEstudos.click();
  await expect(page).toHaveURL('/estudos');
  await expect(linkEstudos).toBeHidden();
});

test('sem sessão, qualquer seção leva ao login', async ({ browser }) => {
  const page = await browser.newPage();
  await page.goto('/financas');
  await expect(page).toHaveURL('/login');
  await page.close();
});
