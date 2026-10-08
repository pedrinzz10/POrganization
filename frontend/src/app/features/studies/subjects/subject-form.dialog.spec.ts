import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { environment } from '../../../../environments/environment';
import { Subject } from '../data/study.model';
import { SubjectFormDialog } from './subject-form.dialog';

const API = environment.apiUrl;
const tick = () => new Promise((resolve) => setTimeout(resolve));

function materia(id: string, nome: string, parcial: Partial<Subject> = {}): Subject {
  return {
    id,
    name: nome,
    color: '#3F51B5',
    priorityOrder: 1,
    sessionsPerWeek: 2,
    lessonMinutes: 50,
    archived: false,
    tags: [],
    studyDays: [],
    lessonMode: 'FREE',
    plannedTotal: 0,
    plannedDone: 0,
    prerequisiteIds: [],
    completed: false,
    blockedBy: [],
    ...parcial,
  };
}

describe('SubjectFormDialog (pré-requisitos)', () => {
  async function abrir(editando: Subject) {
    const dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [SubjectFormDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { subject: editando } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SubjectFormDialog);
    await tick();
    httpMock.expectOne(`${API}/tags`).flush([]);
    httpMock
      .expectOne(`${API}/subjects`)
      .flush([materia('f1', 'Física I'), editando, materia('q', 'Química')]);
    await fixture.whenStable();
    return { fixture, httpMock, dialogRef, element: fixture.nativeElement as HTMLElement };
  }

  // E17 T4 (CA1, CA3)
  it('"Depende de" lista as outras matérias e o salvar manda as escolhidas e o concluída', async () => {
    const { fixture, httpMock, dialogRef } = await abrir(materia('f2', 'Física II'));
    const outras = (fixture.componentInstance as unknown as { outras: () => Subject[] }).outras();
    expect(outras.map((m) => m.name)).toEqual(['Física I', 'Química']);

    fixture.componentInstance.form.patchValue({ prerequisiteIds: ['f1'], completed: true });
    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne({ method: 'PUT', url: `${API}/subjects/f2` });
    expect(req.request.body).toMatchObject({ prerequisiteIds: ['f1'], completed: true });
    req.flush(materia('f2', 'Física II'));
    await salvando;
    expect(dialogRef.close).toHaveBeenCalledWith(true);
    httpMock.verify();
  });

  // E17 T4 (CA3)
  it('com aulas definidas não tem "Matéria concluída" e não marca à mão', async () => {
    const { fixture, httpMock, element } = await abrir(
      materia('f2', 'Física II', { lessonMode: 'PLANNED' }),
    );
    expect(element.textContent).not.toContain('Matéria concluída');

    fixture.componentInstance.form.patchValue({ completed: true });
    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne({ method: 'PUT', url: `${API}/subjects/f2` });
    expect(req.request.body).toMatchObject({ lessonMode: 'PLANNED', completed: false });
    req.flush(materia('f2', 'Física II'));
    await salvando;
    httpMock.verify();
  });
});
