import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { addDays, today } from '../commitments/data/date-range.util';
import { StatsPage } from './stats.page';

const API = environment.apiUrl;
const tick = () => new Promise((resolve) => setTimeout(resolve));
const hoje = today();

const tarefa = (id: string, title: string, position: number) => ({
  id,
  title,
  emoji: null,
  weekDays: [],
  position,
  archived: false,
  createdOn: '2026-01-01',
  reminderTime: null,
  timerMinutes: null,
});

describe('StatsPage', () => {
  let fixture: ComponentFixture<StatsPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StatsPage);
    element = fixture.nativeElement;
    await tick();
    await tick();
  });

  afterEach(() => httpMock.verify());

  function responder(from: string) {
    httpMock
      .expectOne(
        (r) =>
          r.url === `${API}/tasks/history` &&
          r.params.get('from') === from &&
          r.params.get('to') === hoje,
      )
      .flush([
        { date: addDays(hoje, -1), due: 2, done: 2 },
        { date: hoje, due: 2, done: 1 },
      ]);
    httpMock
      .expectOne((r) => r.url === `${API}/study/stats` && r.params.get('from') === from)
      .flush({
        from,
        to: hoje,
        totalMinutes: 95,
        reviewsDone: 3,
        subjects: [
          {
            subjectId: 'j',
            name: 'Java',
            color: null,
            minutes: 95,
            lessons: 2,
            reviews: 3,
            sessionsThisWeek: 1,
            sessionsPerWeek: 2,
          },
          {
            subjectId: 'i',
            name: 'Inglês',
            color: null,
            minutes: 0,
            lessons: 0,
            reviews: 0,
            sessionsThisWeek: 0,
            sessionsPerWeek: 2,
          },
        ],
        weeks: [{ weekStart: from, minutes: 95 }],
        lessons: [],
      });
  }

  // S01 T2 (CA2)
  it('junta tarefas, estudos e finanças do período', async () => {
    responder(addDays(hoje, -29));
    httpMock.expectOne(`${API}/tasks`).flush([tarefa('a', 'Água', 1), tarefa('l', 'Ler', 2)]);
    httpMock.expectOne(`${API}/tasks/stats`).flush([
      { taskId: 'a', streak: 2, completionRate: '50.00' },
      { taskId: 'l', streak: 5, completionRate: '100.00' },
    ]);
    httpMock
      .expectOne(
        (r) => r.url === `${API}/finance/summary` && r.params.get('month') === hoje.slice(0, 7),
      )
      .flush({ month: hoje.slice(0, 7), income: '1000.00', expense: '400.00', net: '600.00' });
    await fixture.whenStable();

    const texto = element.textContent!;
    expect(texto).toContain('75%'); // 3 de 4 devidas
    expect(texto).toContain('3 de 4 no período');
    expect(texto).toContain('🔥 5'); // maior sequência: Ler
    expect(texto).toContain('1h35');
    expect(texto).toContain('Java');
    expect(texto).not.toContain('Inglês'); // matéria sem minutos fica de fora
    expect(texto).toContain('600,00');
    expect(element.querySelectorAll('.calor__dia:not(.calor__dia--fora)').length).toBe(2);
    expect(element.querySelector(".calor__dia[data-nivel='3']")).not.toBeNull();
  });

  it('trocar o período busca de novo tarefas e estudos', async () => {
    responder(addDays(hoje, -29));
    httpMock.match(() => true).forEach((r) => r.flush([]));
    await fixture.whenStable();

    (
      Array.from(element.querySelectorAll('[role=tab]')).find((b) =>
        b.textContent!.includes('7 dias'),
      ) as HTMLElement
    ).click();
    await tick();
    await tick();
    responder(addDays(hoje, -6));
    await fixture.whenStable();
    expect(element.querySelector('[role=tab][aria-selected=true]')!.textContent).toContain(
      '7 dias',
    );
  });

  it('um bloco que falha não derruba os outros', async () => {
    httpMock
      .expectOne((r) => r.url === `${API}/tasks/history`)
      .flush([{ date: hoje, due: 2, done: 1 }]);
    httpMock
      .expectOne((r) => r.url === `${API}/study/stats`)
      .flush({}, { status: 500, statusText: 'Erro' });
    httpMock.expectOne(`${API}/tasks`).flush({}, { status: 500, statusText: 'Erro' });
    httpMock.expectOne(`${API}/tasks/stats`).flush([]);
    httpMock
      .expectOne((r) => r.url === `${API}/finance/summary`)
      .flush({}, { status: 500, statusText: 'Erro' });
    await fixture.whenStable();

    expect(element.textContent).toContain('50%');
    expect(element.textContent).toContain('Não foi possível carregar as finanças.');
  });
});
