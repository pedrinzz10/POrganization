import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { environment } from '../../../../environments/environment';
import { Goal } from '../data/finance.model';
import { GoalsPage } from './goals.page';

const API = `${environment.apiUrl}/finance`;

// Com consultas pendentes, whenStable() espera elas terminarem; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

function meta(mudancas: Partial<Goal> = {}): Goal {
  return {
    id: 'g1', name: 'Reserva', targetAmount: '6000.00', targetDate: '2027-04-30', accountId: null, archived: false,
    saved: '1500.00', remaining: '4500.00', progress: '25.00', monthlyNeeded: '750.00', achieved: false, ...mudancas,
  };
}

describe('GoalsPage', () => {
  let fixture: ComponentFixture<GoalsPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GoalsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialog, useValue: { open: vi.fn() } }],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GoalsPage);
    element = fixture.nativeElement;
    await tick();
    await tick();
    httpMock.expectOne(`${API}/goals`).flush([meta()]);
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const barra = () => element.querySelector('mat-progress-bar[mode="determinate"]')!;
  const botao = (texto: string) =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === texto)!;

  // F14 T2 (CA2)
  it('registrar aporte atualiza o progresso da meta na tela', async () => {
    expect(barra().getAttribute('aria-valuenow')).toBe('25');
    expect(element.textContent).toContain('25,00%');

    botao('Aportar').click();
    await fixture.whenStable();
    fixture.componentInstance['aporte'].patchValue({ amount: '1500.00', date: '2026-10-15' });
    const aportando = fixture.componentInstance.aportar(meta());

    const post = httpMock.expectOne(`${API}/goals/g1/contributions`);
    expect(post.request.body).toEqual({ amount: '1500.00', date: '2026-10-15', note: null });
    post.flush({ id: 'c1', goalId: 'g1', amount: '1500.00', date: '2026-10-15', note: null });
    await tick();
    httpMock.expectOne(`${API}/goals/g1`).flush(meta({ saved: '3000.00', remaining: '3000.00', progress: '50.00', monthlyNeeded: '500.00' }));
    await aportando;
    await fixture.whenStable();

    expect(barra().getAttribute('aria-valuenow')).toBe('50');
    expect(element.textContent).toContain('50,00%');
    expect(element.textContent).toContain('Faltam R$ 3.000,00');
    // O formulário de aporte fecha
    expect(botao('Aportar')).toBeDefined();
  });

  it('mostra quanto guardar por mês até o prazo', () => {
    expect(element.textContent).toContain('Guardar R$ 750,00 por mês até 30/04/2027');
  });
});
