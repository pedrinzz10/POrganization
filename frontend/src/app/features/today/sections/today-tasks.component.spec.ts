import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { DayTask } from '../../tasks/data/task.model';
import { TodayTasksComponent } from './today-tasks.component';

const API = `${environment.apiUrl}/tasks`;
const HOJE = '2026-10-07';
const tick = () => new Promise((resolve) => setTimeout(resolve));

function tarefa(
  id: string,
  titulo: string,
  posicao: number,
  feita = false,
  minutos: number | null = null,
): DayTask {
  return { id, title: titulo, emoji: null, position: posicao, done: feita, timerMinutes: minutos };
}

describe('TodayTasksComponent', () => {
  let fixture: ComponentFixture<TodayTasksComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    localStorage.removeItem('porganization.cronometros');
    snackBar = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TodayTasksComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TodayTasksComponent);
    fixture.componentRef.setInput('today', HOJE);
    element = fixture.nativeElement;
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.removeItem('porganization.cronometros');
    httpMock.verify();
  });

  async function carregar(lista: DayTask[]) {
    await tick();
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/day` && r.params.get('date') === HOJE).flush(lista);
    await fixture.whenStable();
  }

  const titulos = () =>
    Array.from(element.querySelectorAll('.tarefa__titulo')).map((e) => e.textContent!.trim());
  const progresso = () => element.querySelector('.progresso span')!.textContent!.trim();

  // T03 T1 (CA1)
  it('marcar atualiza o progresso e manda a tarefa para o fim antes da API responder; erro volta', async () => {
    await carregar([tarefa('ler', 'Ler', 1), tarefa('agua', 'Água', 2)]);
    expect(progresso()).toBe('0/2 feitas');

    const marcando = fixture.componentInstance.alternar(tarefa('ler', 'Ler', 1));
    await fixture.whenStable();
    expect(progresso()).toBe('1/2 feitas');
    expect(titulos()).toEqual(['Água', 'Ler']);

    const req = httpMock.expectOne(`${API}/ler/completions/${HOJE}`);
    expect(req.request.method).toBe('PUT');
    req.flush({}, { status: 500, statusText: 'Erro' });
    await marcando;
    await fixture.whenStable();

    expect(progresso()).toBe('0/2 feitas');
    expect(titulos()).toEqual(['Ler', 'Água']);
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('desmarcar chama o DELETE', async () => {
    await carregar([tarefa('ler', 'Ler', 1, true)]);

    const desmarcando = fixture.componentInstance.alternar(tarefa('ler', 'Ler', 1, true));
    const req = httpMock.expectOne(`${API}/ler/completions/${HOJE}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await desmarcando;
    await fixture.whenStable();

    expect(progresso()).toBe('0/1 feitas');
  });

  // T03 T2 (CA2)
  it('tudo feito mostra a comemoração; lista vazia convida a criar', async () => {
    await carregar([tarefa('ler', 'Ler', 1, true), tarefa('agua', 'Água', 2, true)]);
    expect(element.textContent).toContain('Tudo feito hoje 🎉');
  });

  it('sem tarefas para hoje, convida a criar a primeira', async () => {
    await carregar([]);
    expect(element.textContent).toContain('Nenhuma tarefa para hoje');
    expect(element.querySelector('a[href="/tarefas"]')).not.toBeNull();
  });

  const botao = (rotulo: string) =>
    element.querySelector<HTMLButtonElement>(`button[aria-label="${rotulo}"]`);
  const tempo = () => element.querySelector('.cronometro__tempo')?.textContent?.trim();

  // T07 T3 (CA2)
  it('cronômetro conta, pausa e, ao zerar, marca a tarefa como feita', async () => {
    await carregar([tarefa('ler', 'Ler', 1, false, 1), tarefa('agua', 'Água', 2)]);
    // Só a tarefa com cronômetro tem o botão
    expect(botao('Iniciar cronômetro de Água')).toBeNull();
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });

    botao('Iniciar cronômetro de Ler')!.click();
    await fixture.whenStable();
    expect(tempo()).toBe('01:00');

    vi.advanceTimersByTime(20_000);
    await fixture.whenStable();
    expect(tempo()).toBe('00:40');

    botao('Pausar Ler')!.click();
    vi.advanceTimersByTime(30_000);
    await fixture.whenStable();
    expect(tempo()).toBe('00:40');

    botao('Retomar Ler')!.click();
    vi.advanceTimersByTime(40_000);
    await fixture.whenStable();

    const req = httpMock.expectOne(`${API}/ler/completions/${HOJE}`);
    expect(req.request.method).toBe('PUT');
    req.flush(null, { status: 204, statusText: 'No Content' });
    vi.useRealTimers();
    await tick();
    await fixture.whenStable();

    expect(progresso()).toBe('1/2 feitas');
    expect(tempo()).toBeUndefined();
    expect(snackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Tarefa feita'),
      'OK',
      expect.anything(),
    );
  });

  it('marcar na mão cancela o cronômetro', async () => {
    await carregar([tarefa('ler', 'Ler', 1, false, 20)]);
    botao('Iniciar cronômetro de Ler')!.click();
    await fixture.whenStable();
    expect(tempo()).toBe('20:00');

    const marcando = fixture.componentInstance.alternar(tarefa('ler', 'Ler', 1, false, 20));
    httpMock
      .expectOne(`${API}/ler/completions/${HOJE}`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await marcando;
    await fixture.whenStable();

    expect(tempo()).toBeUndefined();
    expect(localStorage.getItem('porganization.cronometros')).toBe('{}');
  });
});
