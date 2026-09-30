import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { StudyStats } from '../data/study.model';
import { StudyHistoryPage } from './study-history.page';

const tick = () => new Promise((resolve) => setTimeout(resolve));

const stats: StudyStats = {
  from: '2026-08-10',
  to: '2026-10-04',
  totalMinutes: 125,
  reviewsDone: 2,
  subjects: [
    { subjectId: 'm1', name: 'Java', color: '#1E88E5', minutes: 100, lessons: 2, reviews: 2, sessionsThisWeek: 1, sessionsPerWeek: 3 },
    { subjectId: 'm2', name: 'Inglês', color: '#43A047', minutes: 25, lessons: 1, reviews: 0, sessionsThisWeek: 2, sessionsPerWeek: 2 },
  ],
  weeks: [
    { weekStart: '2026-09-21', minutes: 50 },
    { weekStart: '2026-09-28', minutes: 75 },
  ],
  lessons: [
    { id: 'l1', subjectId: 'm1', title: 'Streams', notes: 'map, filter', studiedAt: '2026-10-01T13:00:00Z', durationMinutes: 50 },
    { id: 'l2', subjectId: 'm2', title: 'Present Perfect', notes: null, studiedAt: '2026-09-30T13:00:00Z', durationMinutes: 25 },
  ],
};

describe('StudyHistoryPage', () => {
  let fixture: ComponentFixture<StudyHistoryPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudyHistoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StudyHistoryPage);
    element = fixture.nativeElement;
    await tick();
    await tick();
  });

  afterEach(() => httpMock.verify());

  async function responder(corpo: StudyStats) {
    httpMock.expectOne((r) => r.url === `${environment.apiUrl}/study/stats`).flush(corpo);
    await fixture.whenStable();
  }

  const linhaDa = (nome: string) =>
    Array.from(element.querySelectorAll<HTMLElement>('.materia')).find((l) => l.textContent!.includes(nome))!;

  // E10 T2 (CA2)
  it('mostra por matéria "X de Y sessões nesta semana"', async () => {
    await responder(stats);

    expect(linhaDa('Java').textContent).toContain('1 de 3 sessões');
    expect(linhaDa('Inglês').textContent).toContain('2 de 2 sessões');
    expect(linhaDa('Inglês').classList).toContain('materia--meta-cumprida');
  });

  it('mostra o gráfico de minutos por semana e as aulas por matéria', async () => {
    await responder(stats);

    expect(element.querySelectorAll('.semana')).toHaveLength(2);
    expect(element.textContent).toContain('2h05 no período');
    const aulasJava = element.querySelector('[data-materia="m1"]')!;
    expect(aulasJava.textContent).toContain('Streams');
    expect(aulasJava.textContent).not.toContain('Present Perfect');
  });

  it('consulta as últimas 8 semanas', () => {
    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/study/stats`);
    const from = new Date(req.request.params.get('from')! + 'T12:00');
    const to = new Date(req.request.params.get('to')! + 'T12:00');
    expect(from.getDay()).toBe(1); // segunda
    expect(to.getDay()).toBe(0); // domingo
    expect(Math.round((to.getTime() - from.getTime()) / 86400000)).toBe(55);
    req.flush(stats);
  });
});
