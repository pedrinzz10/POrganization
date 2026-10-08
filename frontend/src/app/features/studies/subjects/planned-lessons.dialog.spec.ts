import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { environment } from '../../../../environments/environment';
import { PlannedLesson, Subject } from '../data/study.model';
import { PlannedLessonsDialog } from './planned-lessons.dialog';

const API = `${environment.apiUrl}/subjects/java/planned-lessons`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

const JAVA = { id: 'java', name: 'Java', lessonMode: 'PLANNED' } as Subject;

function aula(id: string, titulo: string, posicao: number, feita = false): PlannedLesson {
  return {
    id,
    title: titulo,
    position: posicao,
    lessonId: feita ? 'l-' + id : null,
    studiedAt: null,
  };
}

describe('PlannedLessonsDialog', () => {
  let fixture: ComponentFixture<PlannedLessonsDialog>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlannedLessonsDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { subject: JAVA } },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlannedLessonsDialog);
    element = fixture.nativeElement;
    await tick();
    httpMock
      .expectOne(API)
      .flush([aula('a', 'Variáveis', 1, true), aula('b', 'Laços', 2), aula('c', 'Classes', 3)]);
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const titulos = () =>
    Array.from(element.querySelectorAll('.aula__titulo')).map((e) => e.textContent!.trim());

  // E14 T4 (CA2)
  it('mostra as aulas na ordem, com a estudada marcada', () => {
    expect(titulos()).toEqual(['Variáveis', 'Laços', 'Classes']);
    expect(element.textContent).toContain('1 de 3 estudadas');
    expect(element.querySelectorAll('[aria-label="Estudada"]').length).toBe(1);
  });

  // E14 T4 (CA2)
  it('cola várias linhas e inclui no fim, ignorando as vazias', async () => {
    const dialogo = fixture.componentInstance as unknown as { novas: string };
    dialogo.novas = 'Herança\n\n  Interfaces  \n';
    const salvando = fixture.componentInstance.adicionar();
    const req = httpMock.expectOne({ method: 'POST', url: API });
    expect(req.request.body).toEqual({ titles: ['Herança', 'Interfaces'] });
    req.flush([
      aula('a', 'Variáveis', 1, true),
      aula('b', 'Laços', 2),
      aula('c', 'Classes', 3),
      aula('d', 'Herança', 4),
      aula('e', 'Interfaces', 5),
    ]);
    await salvando;
    await fixture.whenStable();
    expect(titulos()).toEqual(['Variáveis', 'Laços', 'Classes', 'Herança', 'Interfaces']);
    expect(dialogo.novas).toBe('');
  });

  // E14 T4 (CA2)
  it('arrastar manda a nova ordem e, se falhar, volta', async () => {
    const soltando = fixture.componentInstance.soltar({
      previousIndex: 2,
      currentIndex: 0,
    } as CdkDragDrop<PlannedLesson[]>);
    await fixture.whenStable();
    expect(titulos()).toEqual(['Classes', 'Variáveis', 'Laços']);
    const req = httpMock.expectOne({ method: 'PUT', url: `${API}/order` });
    expect(req.request.body).toEqual({ ids: ['c', 'a', 'b'] });
    req.flush({ title: 'Erro' }, { status: 500, statusText: 'Erro' });
    await soltando;
    await fixture.whenStable();
    expect(titulos()).toEqual(['Variáveis', 'Laços', 'Classes']);
    expect(element.querySelector('[role="alert"]')).toBeTruthy();
  });

  // E14 T4 (CA2)
  it('excluir tira da lista e renumera', async () => {
    const excluindo = fixture.componentInstance.excluir(aula('b', 'Laços', 2));
    httpMock
      .expectOne({ method: 'DELETE', url: `${API}/b` })
      .flush(null, { status: 204, statusText: 'No Content' });
    await excluindo;
    await fixture.whenStable();
    expect(titulos()).toEqual(['Variáveis', 'Classes']);
    expect(
      Array.from(element.querySelectorAll('.aula__posicao')).map((e) => e.textContent!.trim()),
    ).toEqual(['1', '2']);
  });
});
