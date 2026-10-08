import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

interface Aula {
  id: string;
  title: string;
  position: number;
  lessonId: string | null;
  studiedAt: string | null;
}

// E14 T6 (CA1, CA2)
test('matéria com aulas definidas: cola a lista do curso e vê o progresso', async ({ page }) => {
  await entrarComSessaoFalsa(page);
  const aulas: Aula[] = [];
  const java = () => ({
    id: 'java',
    name: 'Java',
    color: '#3F51B5',
    priorityOrder: 1,
    sessionsPerWeek: 2,
    lessonMinutes: 50,
    archived: false,
    tags: [],
    studyDays: [],
    lessonMode: 'PLANNED',
    plannedTotal: aulas.length,
    plannedDone: aulas.filter((a) => a.lessonId).length,
  });
  await page.route(`${API}/study/**`, (route) =>
    route.fulfill({ json: { date: '2026-10-08', reviews: [], lessons: [] } }),
  );
  await page.route(`${API}/study/sessions/active`, (route) => route.fulfill({ status: 204 }));
  await page.route(`${API}/tags`, (route) => route.fulfill({ json: [] }));
  await page.route(`${API}/subjects`, (route) => route.fulfill({ json: [java()] }));
  await page.route(`${API}/subjects/java/planned-lessons`, async (route) => {
    if (route.request().method() === 'POST') {
      for (const title of route.request().postDataJSON().titles as string[]) {
        aulas.push({
          id: `a${aulas.length + 1}`,
          title,
          position: aulas.length + 1,
          lessonId: null,
          studiedAt: null,
        });
      }
    }
    await route.fulfill({ json: aulas });
  });

  await page.goto('/estudos');
  await page.getByRole('tab', { name: 'Matérias' }).click();
  await page.getByRole('button', { name: /Aulas de Java: 0 de 0/ }).click();

  const dialogo = page.getByRole('dialog', { name: 'Aulas de Java' });
  await expect(dialogo.getByText('Nenhuma aula ainda')).toBeVisible();
  await dialogo
    .getByLabel('Adicionar aulas (uma por linha)')
    .fill('Introdução\nVariáveis e tipos\nLaços');
  await dialogo.getByRole('button', { name: 'Adicionar' }).click();

  await expect(dialogo.locator('.aula__titulo')).toHaveText([
    'Introdução',
    'Variáveis e tipos',
    'Laços',
  ]);
  await expect(dialogo.getByText('0 de 3 estudadas')).toBeVisible();
  await dialogo.getByRole('button', { name: 'Fechar' }).click();
  await expect(page.getByRole('button', { name: /Aulas de Java: 0 de 3/ })).toBeVisible();
});
