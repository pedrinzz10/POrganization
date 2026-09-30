import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { Occurrence } from '../data/commitment.model';
import { DayViewComponent } from './day-view.component';

function ocorrencia(parcial: Partial<Occurrence>): Occurrence {
  return {
    commitmentId: 'id-' + (parcial.title ?? 'x'),
    occurrenceDate: '2026-10-03',
    title: 'x',
    startTime: null,
    endTime: null,
    allDay: false,
    done: false,
    recurring: false,
    description: null,
    location: null,
    ...parcial,
  };
}

describe('DayViewComponent', () => {
  let fixture: ComponentFixture<DayViewComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  async function renderizar(ocorrencias: Occurrence[]) {
    fixture.componentRef.setInput('occurrences', ocorrencias);
    await fixture.whenStable();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DayViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DayViewComponent);
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  // espera a resposta da API (ou o erro) ser processada e a tela atualizar
  async function settle() {
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  const linhas = () => Array.from(element.querySelectorAll<HTMLElement>('.day-item'));

  // C07 T1 (CA1)
  it('mostra os de dia todo no topo e depois por horário', async () => {
    await renderizar([
      ocorrencia({ title: 'Reunião', startTime: '10:00' }),
      ocorrencia({ title: 'Academia', startTime: '07:00' }),
      ocorrencia({ title: 'Feriado', allDay: true }),
    ]);

    expect(linhas().map((l) => l.querySelector('.day-item__title')!.textContent!.trim())).toEqual([
      'Feriado',
      'Academia',
      'Reunião',
    ]);
    expect(linhas()[0].textContent).toContain('Dia todo');
  });

  // C07 T2 (CA2)
  it('concluir uma ocorrência recorrente chama o PATCH da ocorrência e risca o item', async () => {
    await renderizar([ocorrencia({ commitmentId: 'serie-1', title: 'Remédio', recurring: true, startTime: '08:00' })]);

    element.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${environment.apiUrl}/commitments/serie-1/occurrences/2026-10-03`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ done: true });
    expect(linhas()[0].classList).toContain('done');
    req.flush(ocorrencia({ commitmentId: 'serie-1', title: 'Remédio', recurring: true, done: true }));
  });

  // C07 T2 (CA2)
  it('concluir um compromisso único chama o PATCH /done', async () => {
    await renderizar([ocorrencia({ commitmentId: 'unico-1', title: 'Dentista', startTime: '14:00' })]);

    element.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    await fixture.whenStable();

    const req = httpMock.expectOne(`${environment.apiUrl}/commitments/unico-1/done`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ done: true });
    req.flush({});
  });

  it('se a API falhar, desfaz o risco', async () => {
    await renderizar([ocorrencia({ commitmentId: 'unico-1', title: 'Dentista', startTime: '14:00' })]);

    element.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    await fixture.whenStable();
    httpMock.expectOne(`${environment.apiUrl}/commitments/unico-1/done`).flush({}, { status: 500, statusText: 'Erro' });
    await settle();

    expect(linhas()[0].classList).not.toContain('done');
  });

  it('sem compromissos, mostra um aviso', async () => {
    await renderizar([]);
    expect(element.textContent).toContain('Nenhum compromisso');
  });
});
