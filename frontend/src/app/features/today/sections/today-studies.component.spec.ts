import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { StudyToday } from '../../studies/data/study.model';
import { TodayStudiesComponent } from './today-studies.component';

const plano: Omit<StudyToday, 'date'> = {
  reviews: [
    { lessonId: 'l1', subjectId: 'm1', subjectName: 'Java', lessonTitle: 'Streams', dueDate: '2026-09-29', daysOverdue: 2, reviewMinutes: 25 },
    { lessonId: 'l2', subjectId: 'm2', subjectName: 'Inglês', lessonTitle: 'Present Perfect', dueDate: '2026-10-01', daysOverdue: 0, reviewMinutes: 15 },
  ],
  lessons: [
    { subjectId: 'm2', subjectName: 'Inglês', color: null, priorityOrder: 1, suggestedMinutes: 30, doneThisWeek: 0, sessionsPerWeek: 2 },
  ],
};

describe('TodayStudiesComponent', () => {
  let fixture: ComponentFixture<TodayStudiesComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TodayStudiesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TodayStudiesComponent);
    element = fixture.nativeElement;
    fixture.componentRef.setInput('today', '2026-10-01');
    fixture.componentRef.setInput('plan', plano);
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const itens = () => Array.from(element.querySelectorAll<HTMLElement>('.estudo'));

  // E11 T2 (CA2)
  it('revisões vencidas vêm antes das aulas, com o selo de atraso', () => {
    expect(itens()[0].textContent).toContain('Streams');
    expect(itens()[0].textContent).toContain('Revisão · 2 dias de atraso');
    expect(itens()[1].textContent).toContain('Present Perfect');
    expect(itens()[1].textContent).not.toContain('atraso');
    expect(itens()[2].textContent).toContain('Inglês');
    expect(itens()[2].textContent).toContain('Aula');
  });

  it('um dia de atraso usa o singular', async () => {
    fixture.componentRef.setInput('plan', { ...plano, reviews: [{ ...plano.reviews[0], dueDate: '2026-09-30' }] });
    await fixture.whenStable();
    expect(itens()[0].textContent).toContain('Revisão · 1 dia de atraso');
  });

  it('o botão inicia a revisão e abre o timer em Estudos', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    itens()[0].querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${environment.apiUrl}/study/sessions`);
    expect(req.request.body).toEqual({ subjectId: 'm1', type: 'REVIEW', lessonId: 'l1' });
    req.flush({});
    await new Promise((resolve) => setTimeout(resolve));
    expect(navigate).toHaveBeenCalledWith(['/estudos']);
  });
});
