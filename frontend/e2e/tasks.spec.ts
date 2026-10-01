import { expect, Page, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';
const HOJE = '2026-10-07';

interface Tarefa {
  id: string;
  title: string;
  emoji: string | null;
  weekDays: string[];
  position: number;
  archived: boolean;
  createdOn: string;
  reminderTime: string | null;
  timerMinutes: number | null;
}

/** API de tarefas de mentira, com estado: criar, marcar, sequência e excluir. */
async function apiFalsa(page: Page) {
  const tarefas: Tarefa[] = [];
  const feitas = new Set<string>();

  await page.route(`${API}/today`, (route) =>
    route.fulfill({
      json: {
        date: HOJE,
        timezone: 'America/Sao_Paulo',
        commitments: [],
        studies: { reviews: [], lessons: [] },
        finance: null,
      },
    }),
  );
  await page.route(`${API}/tasks**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const caminho = url.pathname.replace('/api/tasks', '');
    const metodo = request.method();

    if (caminho === '' && metodo === 'GET') {
      return route.fulfill({ json: tarefas });
    }
    if (caminho === '' && metodo === 'POST') {
      const corpo = request.postDataJSON();
      const nova: Tarefa = {
        ...corpo,
        id: `t${tarefas.length + 1}`,
        position: tarefas.length + 1,
        archived: false,
        createdOn: HOJE,
      };
      tarefas.push(nova);
      return route.fulfill({ status: 201, json: nova });
    }
    if (caminho === '/stats') {
      return route.fulfill({
        json: tarefas.map((t) => ({
          taskId: t.id,
          streak: feitas.has(t.id) ? 1 : 0,
          completionRate: feitas.has(t.id) ? '100.00' : null,
        })),
      });
    }
    if (caminho === '/day') {
      return route.fulfill({
        json: tarefas
          .filter((t) => !t.archived)
          .map((t) => ({
            id: t.id,
            title: t.title,
            emoji: t.emoji,
            position: t.position,
            done: feitas.has(t.id),
            timerMinutes: t.timerMinutes,
          })),
      });
    }
    const conclusao = caminho.match(/^\/(\w+)\/completions\/[\d-]+$/);
    if (conclusao) {
      if (metodo === 'PUT') feitas.add(conclusao[1]);
      else feitas.delete(conclusao[1]);
      return route.fulfill({ status: 204 });
    }
    const unica = caminho.match(/^\/(\w+)$/);
    if (unica && metodo === 'DELETE') {
      tarefas.splice(
        tarefas.findIndex((t) => t.id === unica[1]),
        1,
      );
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ status: 404 });
  });
}

// T04 T3 (CA3)
test('cria a tarefa pelo menu, marca na tela Hoje, vê a sequência subir e exclui com confirmação', async ({
  page,
}) => {
  await entrarComSessaoFalsa(page);
  await apiFalsa(page);
  await page.goto('/hoje');
  const menu = page.getByRole('navigation', { name: 'Menu principal' });

  await menu.getByRole('link', { name: 'Tarefas' }).click();
  await expect(page).toHaveURL('/tarefas');
  await page.getByRole('button', { name: 'Nova tarefa' }).click();
  await page.getByRole('radio', { name: 'Leitura' }).click();
  await page.getByLabel('Tarefa', { exact: true }).fill('Ler 20 min');
  await page.getByRole('button', { name: 'Salvar' }).click();

  const lista = page.getByRole('list', { name: 'Tarefas ativas' });
  await expect(lista).toContainText('Ler 20 min');
  await expect(lista).toContainText('todo dia');
  await expect(lista.getByLabel('Sequência de Ler 20 min')).toHaveText('🔥 0');

  await menu.getByRole('link', { name: 'Hoje' }).click();
  await page.getByRole('checkbox', { name: /Ler 20 min/ }).check();
  await expect(page.getByText('1/1 feitas')).toBeVisible();
  await expect(page.getByText('Tudo feito hoje 🎉')).toBeVisible();

  await menu.getByRole('link', { name: 'Tarefas' }).click();
  await expect(lista.getByLabel('Sequência de Ler 20 min')).toHaveText('🔥 1');

  await lista.getByRole('button', { name: 'Excluir Ler 20 min' }).click();
  const confirmacao = page.getByRole('dialog');
  await expect(confirmacao).toContainText('Excluir "Ler 20 min"?');
  await confirmacao.getByRole('button', { name: 'Excluir' }).click();
  await expect(lista).toContainText('Nenhuma tarefa ainda');
});

// T07 T4 (CA1, CA2)
test('tarefa com cronômetro: inicia na tela Hoje e, ao zerar, fica feita', async ({ page }) => {
  await page.clock.install();
  await entrarComSessaoFalsa(page);
  await apiFalsa(page);
  await page.goto('/tarefas');

  await page.getByRole('button', { name: 'Nova tarefa' }).click();
  await page.getByLabel('Tarefa', { exact: true }).fill('Meditar');
  await page.getByLabel('Cronômetro (minutos)').fill('1');
  await page.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByRole('list', { name: 'Tarefas ativas' })).toContainText('1 min');

  await page
    .getByRole('navigation', { name: 'Menu principal' })
    .getByRole('link', { name: 'Hoje' })
    .click();
  await page.getByRole('button', { name: 'Iniciar cronômetro de Meditar' }).click();
  const tempo = page.getByRole('timer', { name: 'Tempo restante de Meditar' });
  await expect(tempo).toHaveText('01:00');

  await page.clock.runFor(20_000);
  await expect(tempo).toHaveText('00:40');

  await page.clock.runFor(41_000);
  await expect(page.getByText('1/1 feitas')).toBeVisible();
  await expect(page.getByText('Tarefa feita!')).toBeVisible();
  await expect(tempo).toHaveCount(0);
});
