import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

const base = {
  color: '#3F51B5',
  sessionsPerWeek: 2,
  lessonMinutes: 50,
  archived: false,
  tags: [],
  studyDays: [],
  lessonMode: 'PLANNED',
  plannedTotal: 108,
  plannedDone: 45,
  prerequisiteIds: [] as string[],
  completed: false,
  blockedBy: [] as object[],
};

// E17 T6 (CA1, CA2)
test('Física II depende de Física I: escolhe no formulário e a lista mostra o cadeado', async ({
  page,
}) => {
  await entrarComSessaoFalsa(page);
  let f2 = {
    ...base,
    id: 'f2',
    name: 'Física II',
    priorityOrder: 2,
    plannedTotal: 116,
    plannedDone: 0,
  };
  const enviados: unknown[] = [];
  await page.route(`${API}/study/**`, (r) =>
    r.fulfill({ json: { date: '2026-10-08', reviews: [], lessons: [] } }),
  );
  await page.route(`${API}/study/sessions/active`, (r) => r.fulfill({ status: 204 }));
  await page.route(`${API}/tags`, (r) => r.fulfill({ json: [] }));
  await page.route(`${API}/subjects`, (r) =>
    r.fulfill({ json: [{ ...base, id: 'f1', name: 'Física I', priorityOrder: 1 }, f2] }),
  );
  await page.route(`${API}/subjects/f2`, (r) => {
    enviados.push(r.request().postDataJSON());
    f2 = {
      ...f2,
      prerequisiteIds: ['f1'],
      blockedBy: [{ id: 'f1', name: 'Física I', done: 45, total: 108 }],
    };
    return r.fulfill({ json: f2 });
  });

  await page.goto('/estudos');
  await page.getByRole('tab', { name: 'Matérias' }).click();
  await page.getByRole('button', { name: 'Física II', exact: true }).click();
  await page.getByRole('combobox', { name: 'Depende de' }).click();
  await page.getByRole('option', { name: 'Física I' }).click();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Salvar' }).click();

  expect(enviados[0]).toMatchObject({ prerequisiteIds: ['f1'] });
  await expect(page.locator('.materia__estado')).toHaveText(/Depois de Física I \(45\/108\)/);
});
