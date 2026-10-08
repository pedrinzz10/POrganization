import { expect, Page, test } from '@playwright/test';
import { entrarComSessaoFalsa } from './support/session';

const API = 'http://localhost:8080/api';

interface Item {
  kind: 'LESSON';
  subjectId: string;
  subjectName: string;
  color: string;
  title: string | null;
  minutes: number;
  sessionType: 'LESSON';
  overdue: boolean;
  pinned: boolean;
  plannedLessonId: string | null;
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function dias(from: string, to: string): string[] {
  const lista: string[] = [];
  for (let d = new Date(from + 'T12:00'); iso(d) <= to; d.setDate(d.getDate() + 1)) {
    lista.push(iso(d));
  }
  return lista;
}

/** API de mentira do plano: semana automática com Java hoje; gerar põe Java nos dois últimos dias. */
async function apiFalsa(page: Page) {
  const hoje = iso(new Date());
  let planejada = false;
  let javaEm = [hoje];
  const aulas = ['Variáveis', 'Laços', 'Classes'];
  const semana = (from: string, to: string) => {
    let n = 0;
    return dias(from, to).map((date) => ({
      date,
      planned: planejada,
      items: javaEm.includes(date)
        ? [
            {
              kind: 'LESSON',
              subjectId: 'java',
              subjectName: 'Java',
              color: '#3F51B5',
              title: aulas[n] ?? null,
              minutes: 50,
              sessionType: 'LESSON',
              overdue: false,
              pinned: false,
              plannedLessonId: `p${n++}`,
            } satisfies Item,
          ]
        : [],
    }));
  };
  await page.route(`${API}/study/today`, (r) =>
    r.fulfill({ json: { date: hoje, reviews: [], lessons: [] } }),
  );
  await page.route(`${API}/study/sessions/active`, (r) => r.fulfill({ status: 204 }));
  await page.route(`${API}/subjects`, (r) => r.fulfill({ json: [] }));
  await page.route(`${API}/tags`, (r) => r.fulfill({ json: [] }));
  await page.route(`${API}/study/calendar?**`, (r) => {
    const url = new URL(r.request().url());
    return r.fulfill({ json: semana(url.searchParams.get('from')!, url.searchParams.get('to')!) });
  });
  await page.route(`${API}/study/week/generate?**`, (r) => {
    planejada = true;
    const domingo = dias(hoje, iso(new Date(Date.now() + 6 * 86400000))).find(
      (d) => new Date(d + 'T12:00').getDay() === 0,
    )!;
    javaEm = [...new Set([hoje, domingo])];
    return r.fulfill({ json: [] });
  });
  await page.route(`${API}/study/week/slots?**`, (r) => {
    const dia = new URL(r.request().url()).searchParams.get('day')!;
    javaEm = javaEm.filter((d) => d !== dia);
    return r.fulfill({ json: [] });
  });
  return { hoje };
}

// E15 T7 (CA1, CA2, CA3)
test('gera a semana, vê a aula definida da vez e tira uma aula', async ({ page }) => {
  await entrarComSessaoFalsa(page);
  await apiFalsa(page);

  await page.goto('/estudos');
  await page.getByRole('tab', { name: 'Agenda' }).click();

  await expect(page.getByText('Previsão automática')).toBeVisible();
  await expect(page.locator('.semana__dia--hoje')).toContainText('Java: Variáveis');

  await page.getByRole('button', { name: 'Gerar semana' }).click();
  await expect(page.getByText('Semana gerada.')).toBeVisible();
  await expect(page.getByText('Plano da semana')).toBeVisible();

  await page
    .locator('.semana__dia--hoje')
    .getByRole('button', { name: /Tirar a aula de Java/ })
    .click();
  await expect(page.getByText('Aula de Java tirada do dia.')).toBeVisible();
  await expect(page.locator('.semana__dia--hoje')).not.toContainText('Java');
});
