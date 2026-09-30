import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../environments/environment';
import { DailyTask } from './data/task.model';
import { TasksPage } from './tasks.page';

const API = `${environment.apiUrl}/tasks`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

function tarefa(id: string, posicao: number): DailyTask {
  return {
    id,
    title: id.toUpperCase(),
    emoji: null,
    weekDays: ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'],
    position: posicao,
    archived: false,
    createdOn: '2026-10-01',
    reminderTime: null,
  };
}

describe('TasksPage', () => {
  let fixture: ComponentFixture<TasksPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    snackBar = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [TasksPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TasksPage);
    element = fixture.nativeElement;
    await tick();
    await tick();
    httpMock.expectOne(API).flush([tarefa('a', 1), tarefa('b', 2), tarefa('c', 3)]);
    httpMock.expectOne(`${API}/stats`).flush([
      { taskId: 'a', streak: 5, completionRate: '83.33' },
      { taskId: 'b', streak: 0, completionRate: null },
    ]);
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const nomes = () =>
    Array.from(element.querySelectorAll('.lista .tarefa__nome')).map((e) => e.textContent!.trim());

  const soltar = (de: number, para: number) =>
    fixture.componentInstance.soltar({ previousIndex: de, currentIndex: para } as CdkDragDrop<
      DailyTask[]
    >);

  // T04 T2 (CA2)
  it('arrastar a 3ª para o topo chama PUT /order com a nova ordem', async () => {
    const soltando = soltar(2, 0);
    await fixture.whenStable();
    expect(nomes()).toEqual(['C', 'A', 'B']);

    const req = httpMock.expectOne(`${API}/order`);
    expect(req.request.body).toEqual({ ids: ['c', 'a', 'b'] });
    req.flush([tarefa('c', 1), tarefa('a', 2), tarefa('b', 3)]);
    await soltando;
  });

  it('se o PUT /order falhar, a lista volta', async () => {
    const soltando = soltar(2, 0);
    httpMock.expectOne(`${API}/order`).flush({}, { status: 500, statusText: 'Erro' });
    await soltando;
    await fixture.whenStable();

    expect(nomes()).toEqual(['A', 'B', 'C']);
    expect(snackBar.open).toHaveBeenCalled();
  });

  it('mostra a sequência e a % de cada tarefa', () => {
    expect(element.textContent).toContain('🔥 5');
    expect(element.textContent).toContain('83,33% em 30 dias');
    expect(element.textContent).toContain('sem histórico');
  });
});
