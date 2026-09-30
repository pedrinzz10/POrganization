import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { YearViewComponent } from './year-view.component';

// Com a consulta pendente, whenStable() espera ela terminar; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('YearViewComponent', () => {
  let fixture: ComponentFixture<YearViewComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [YearViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(YearViewComponent);
    element = fixture.nativeElement;
    fixture.componentRef.setInput('year', 2026);
  });

  afterEach(() => httpMock.verify());

  // C08 T3 (CA3)
  it('faz uma única consulta de 01/01 a 31/12', async () => {
    await tick();
    await tick();

    const reqs = httpMock.match(`${environment.apiUrl}/commitments?from=2026-01-01&to=2026-12-31`);
    expect(reqs).toHaveLength(1);
    reqs[0].flush([]);
  });

  it('desenha 12 mini-meses e marca os dias com compromisso', async () => {
    await tick();
    await tick();
    httpMock.expectOne(`${environment.apiUrl}/commitments?from=2026-01-01&to=2026-12-31`).flush([
      { commitmentId: 'a', occurrenceDate: '2026-03-10', title: 'x', startTime: null, endTime: null, allDay: true, done: false, recurring: false, description: null, location: null },
    ]);
    await fixture.whenStable();

    expect(element.querySelectorAll('app-mini-month')).toHaveLength(12);
    expect(element.querySelector('[data-date="2026-03-10"]')!.classList).toContain('ocupado');
    expect(element.querySelector('[data-date="2026-03-11"]')!.classList).not.toContain('ocupado');
  });
});
