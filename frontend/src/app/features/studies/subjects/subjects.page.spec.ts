import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../../environments/environment';
import { Subject } from '../data/study.model';
import { SubjectsPage } from './subjects.page';

const API = environment.apiUrl;

function materia(nome: string, ordem: number, tags: { id: string; name: string }[] = []): Subject {
  return { id: `id-${nome}`, name: nome, color: null, priorityOrder: ordem, sessionsPerWeek: 2, lessonMinutes: 50, archived: false, tags, studyDays: [], lessonMode: 'FREE', plannedTotal: 0, plannedDone: 0, prerequisiteIds: [], completed: false, blockedBy: [] };
}

// Com consultas pendentes, whenStable() espera elas terminarem; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('SubjectsPage', () => {
  let fixture: ComponentFixture<SubjectsPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const linguas = { id: 't1', name: 'línguas' };
  const faculdade = { id: 't2', name: 'faculdade' };

  beforeEach(async () => {
    snackBar = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [SubjectsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatSnackBar, useValue: snackBar }],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SubjectsPage);
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function carregar(materias: Subject[]) {
    await tick();
    await tick();
    httpMock.expectOne(`${API}/subjects`).flush(materias);
    httpMock.expectOne(`${API}/tags`).flush([faculdade, linguas]);
    await fixture.whenStable();
  }

  const nomes = () => Array.from(element.querySelectorAll('.materia__nome')).map((e) => e.textContent!.trim());

  function soltar(de: number, para: number) {
    fixture.componentInstance.drop({ previousIndex: de, currentIndex: para } as CdkDragDrop<Subject[]>);
  }

  // E03 T1 (CA1)
  it('arrastar chama PUT /order com a nova ordem', async () => {
    await carregar([materia('A', 1), materia('B', 2), materia('C', 3)]);

    soltar(2, 0);
    await fixture.whenStable();

    expect(nomes()).toEqual(['C', 'A', 'B']);
    const req = httpMock.expectOne(`${API}/subjects/order`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ ids: ['id-C', 'id-A', 'id-B'] });
    req.flush([materia('C', 1), materia('A', 2), materia('B', 3)]);
  });

  // E03 T2 (CA2)
  it('se o PUT falhar, a lista volta à ordem anterior e mostra erro', async () => {
    await carregar([materia('A', 1), materia('B', 2), materia('C', 3)]);

    soltar(2, 0);
    await fixture.whenStable();
    httpMock.expectOne(`${API}/subjects/order`).flush({}, { status: 500, statusText: 'Erro' });
    await tick();
    await fixture.whenStable();

    expect(nomes()).toEqual(['A', 'B', 'C']);
    expect(snackBar.open).toHaveBeenCalled();
  });

  // E03 T3 (CA3)
  it('clicar num chip de tag filtra a lista', async () => {
    await carregar([materia('Inglês', 1, [linguas]), materia('Java', 2, [faculdade])]);

    const chip = Array.from(element.querySelectorAll<HTMLElement>('.filtro mat-chip-option')).find((c) =>
      c.textContent!.includes('línguas'),
    )!;
    chip.click();
    await fixture.whenStable();

    expect(nomes()).toEqual(['Inglês']);
  });

  it('com filtro ativo, arrastar fica desligado (a ordem é da lista inteira)', async () => {
    await carregar([materia('Inglês', 1, [linguas]), materia('Java', 2, [faculdade])]);
    fixture.componentInstance.filtro.set('línguas');
    await fixture.whenStable();

    expect(element.querySelector('.lista')!.classList).toContain('cdk-drop-list-disabled');
  });

  it('mudar as sessões por semana na linha salva a matéria', async () => {
    await carregar([materia('Java', 1, [faculdade])]);

    element.querySelector<HTMLButtonElement>('button[aria-label="Mais uma sessão de Java"]')!.click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${API}/subjects/id-Java`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      name: 'Java', color: null, sessionsPerWeek: 3, lessonMinutes: 50, tagIds: ['t2'], archived: false,
    });
    req.flush({ ...materia('Java', 1, [faculdade]), sessionsPerWeek: 3 });
    await tick();
    await fixture.whenStable();
    expect(element.textContent).toContain('3x por semana');
  });

  // E14 T5 (CA1)
  it('matéria com aulas definidas mostra o botão com o progresso', async () => {
    await carregar([{ ...materia('Java', 1), lessonMode: 'PLANNED', plannedTotal: 12, plannedDone: 3 }, materia('Inglês', 2)]);
    const botoes = Array.from(element.querySelectorAll('.materia__aulas'));
    expect(botoes.length).toBe(1);
    expect(botoes[0].textContent).toContain('Aulas 3/12');
  });

  // E17 T5 (CA2)
  it('matéria bloqueada mostra de quem depende e a concluída aparece marcada', async () => {
    await carregar([
      { ...materia('Física I', 1), completed: true },
      {
        ...materia('Física II', 2),
        blockedBy: [{ id: 'm', name: 'Matemática', done: 45, total: 108 }, { id: 'i', name: 'Inglês', done: 0, total: 0 }],
      },
    ]);
    const estados = Array.from(element.querySelectorAll('.materia__estado')).map((e) => e.textContent!.trim());
    expect(estados).toEqual(['check_circle Concluída', 'lock Depois de Matemática (45/108), Inglês']);
  });
});
