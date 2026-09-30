import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CommitmentsPage } from './commitments.page';
import { today, weekRange } from './data/date-range.util';

const URL = `${environment.apiUrl}/commitments`;

describe('CommitmentsPage', () => {
  let fixture: ComponentFixture<CommitmentsPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  // Enquanto uma consulta está pendente, whenStable() espera o rxResource terminar (e ele só
  // termina quando o teste responde). Por isso aqui só deixamos a fila de tarefas andar.
  const tick = () => new Promise((resolve) => setTimeout(resolve));

  async function consultaPendente() {
    await tick();
    await tick();
    return httpMock.expectOne((r) => r.method === 'GET' && r.url === URL);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommitmentsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CommitmentsPage);
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function abrirAba(nome: string) {
    const aba = Array.from(element.querySelectorAll<HTMLElement>('[role="tab"]')).find((t) =>
      t.textContent!.includes(nome),
    )!;
    aba.click();
    await tick();
  }

  it('abre em Hoje consultando só o dia de hoje', async () => {
    const req = await consultaPendente();
    expect(req.request.params.get('from')).toBe(today());
    expect(req.request.params.get('to')).toBe(today());
    req.flush([]);
  });

  // C07 (CA3): na aba Semana, "próxima" carrega a semana seguinte
  it('Semana consulta seg a dom e "Próxima semana" carrega a seguinte', async () => {
    (await consultaPendente()).flush([]);
    await abrirAba('Semana');

    const semana = weekRange(today());
    let req = await consultaPendente();
    expect(req.request.params.get('from')).toBe(semana.from);
    expect(req.request.params.get('to')).toBe(semana.to);
    req.flush([]);
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('button[aria-label="Próxima semana"]')!.click();

    req = await consultaPendente();
    const seguinte = weekRange(req.request.params.get('from')!);
    expect(seguinte.from > semana.from).toBe(true);
    expect(req.request.params.get('to')).toBe(seguinte.to);
    req.flush([]);
  });
});
