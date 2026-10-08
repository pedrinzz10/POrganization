import { expect, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

// E16 T5 (CA1)
test('estudar a aula da lista: nome na sugestão, no timer e no título', async ({ page }) => {
  await entrarComSessaoFalsa(page);
  let ativa: object | null = null;
  const inicios: unknown[] = [];

  await page.route(`${API}/study/today`, (r) =>
    r.fulfill({
      json: {
        date: '2026-10-08',
        reviews: [],
        lessons: [
          {
            subjectId: 'java',
            subjectName: 'Java',
            color: '#3F51B5',
            priorityOrder: 1,
            suggestedMinutes: 50,
            doneThisWeek: 1,
            sessionsPerWeek: 3,
            plannedLessonId: 'p2',
            plannedLessonTitle: 'Laços',
          },
        ],
      },
    }),
  );
  await page.route(`${API}/study/sessions/active`, (r) =>
    ativa ? r.fulfill({ json: ativa }) : r.fulfill({ status: 204 }),
  );
  await page.route(`${API}/study/sessions`, (r) => {
    inicios.push(r.request().postDataJSON());
    ativa = {
      id: 's1',
      subjectId: 'java',
      subjectName: 'Java',
      lessonId: null,
      type: 'LESSON',
      status: 'RUNNING',
      startedAt: new Date().toISOString(),
      endedAt: null,
      pausedSeconds: 0,
      pausedAt: null,
      elapsedSeconds: 0,
      plannedMinutes: 50,
      plannedLessonId: 'p2',
      plannedLessonTitle: 'Laços',
    };
    return r.fulfill({ status: 201, json: ativa });
  });

  await page.goto('/estudos');
  await expect(page.getByText('Java: Laços')).toBeVisible();
  await page.getByRole('button', { name: 'Estudar' }).click();

  await expect(page.locator('.timer__materia')).toContainText('Laços');
  expect(inicios).toEqual([{ subjectId: 'java', type: 'LESSON', plannedLessonId: 'p2' }]);
  await page.getByRole('button', { name: 'Concluir' }).click();
  await expect(page.getByLabel('O que você estudou?')).toHaveValue('Laços');
});
