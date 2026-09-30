import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { StudySession } from '../data/study.model';
import { StudySessionComponent } from './study-session.component';

const BASE = `${environment.apiUrl}/study/sessions`;

function sessao(parcial: Partial<StudySession> = {}): StudySession {
  return {
    id: 's1',
    subjectId: 'm1',
    subjectName: 'Java',
    lessonId: null,
    type: 'LESSON',
    status: 'RUNNING',
    startedAt: '2026-10-01T10:00:00Z',
    endedAt: null,
    pausedSeconds: 0,
    pausedAt: null,
    elapsedSeconds: 0,
    plannedMinutes: 25,
    ...parcial,
  };
}

describe('StudySessionComponent', () => {
  let fixture: ComponentFixture<StudySessionComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    // só o intervalo e o relógio são falsos; o setTimeout do Angular continua real
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    vi.setSystemTime(new Date('2026-10-01T10:00:00Z'));
    await TestBed.configureTestingModule({
      imports: [StudySessionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StudySessionComponent);
    element = fixture.nativeElement;
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  async function mostrar(s: StudySession) {
    fixture.componentRef.setInput('session', s);
    await fixture.whenStable();
  }

  // Espera a resposta da API ser aplicada (o setTimeout é real) antes de mexer no relógio falso
  async function settle() {
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  async function avancar(ms: number) {
    vi.advanceTimersByTime(ms);
    await fixture.whenStable();
  }

  const relogio = () => element.querySelector('.timer__tempo')!.textContent!.trim();
  const botao = (texto: string) =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === texto);

  // E09 T1 (CA1)
  it('conta regressivamente a cada segundo e para quando pausado', async () => {
    await mostrar(sessao());
    expect(relogio()).toBe('25:00');

    await avancar(3000);
    expect(relogio()).toBe('24:57');

    botao('Pausar')!.click();
    await fixture.whenStable();
    httpMock.expectOne(`${BASE}/s1/pause`).flush(sessao({ status: 'PAUSED', elapsedSeconds: 3, pausedAt: '2026-10-01T10:00:03Z' }));
    await settle();

    await avancar(5000);
    expect(relogio()).toBe('24:57');
  });

  // E09 T1 (CA1)
  it('ao zerar avisa, continua contando e não encerra sozinho', async () => {
    await mostrar(sessao({ elapsedSeconds: 25 * 60 - 2 }));

    await avancar(2000);
    expect(relogio()).toBe('00:00');
    expect(element.querySelector('.timer')!.classList).toContain('finished');
    expect(element.textContent).toContain('Tempo sugerido concluído');

    await avancar(5000);
    expect(relogio()).toBe('+00:05');
    // nenhuma requisição de finish: a sessão continua aberta
    httpMock.expectNone(`${BASE}/s1/finish`);
    expect(botao('Concluir')).toBeDefined();
  });

  it('retomar volta a contar a partir do tempo da API', async () => {
    await mostrar(sessao({ status: 'PAUSED', elapsedSeconds: 60 }));
    expect(relogio()).toBe('24:00');

    botao('Retomar')!.click();
    await fixture.whenStable();
    httpMock.expectOne(`${BASE}/s1/resume`).flush(sessao({ status: 'RUNNING', elapsedSeconds: 60 }));
    await settle();

    await avancar(10000);
    expect(relogio()).toBe('23:50');
  });

  // E09 T2 (CA2)
  it('revisão só termina com uma das 3 notas, enviada no finish', async () => {
    await mostrar(sessao({ type: 'REVIEW', lessonId: 'l1', plannedMinutes: 10 }));
    expect(botao('Concluir')).toBeUndefined();

    botao('Fácil')!.click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${BASE}/s1/finish`);
    expect(req.request.body).toEqual({ grade: 'FACIL' });
    req.flush(sessao({ type: 'REVIEW', status: 'FINISHED' }));
  });

  it('aula pede título e notas para concluir', async () => {
    await mostrar(sessao());
    const encerradas: StudySession[] = [];
    fixture.componentInstance.ended.subscribe((s) => encerradas.push(s));

    botao('Concluir')!.click();
    await fixture.whenStable();
    const titulo = element.querySelector<HTMLInputElement>('input[formControlName="title"]')!;
    titulo.value = 'Streams';
    titulo.dispatchEvent(new Event('input'));
    const notas = element.querySelector<HTMLTextAreaElement>('textarea[formControlName="notes"]')!;
    notas.value = 'map e filter';
    notas.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    botao('Salvar aula')!.click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${BASE}/s1/finish`);
    expect(req.request.body).toEqual({ title: 'Streams', notes: 'map e filter' });
    req.flush(sessao({ status: 'FINISHED' }));
    await settle();
    expect(encerradas).toHaveLength(1);
  });
});
