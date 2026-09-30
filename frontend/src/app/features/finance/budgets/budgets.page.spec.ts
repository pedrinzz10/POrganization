import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { BudgetLevel, BudgetStatus } from '../data/finance.model';
import { BudgetsPage } from './budgets.page';

const API = `${environment.apiUrl}/finance`;

// Com consultas pendentes, whenStable() espera elas terminarem; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

function orcamento(nome: string, gasto: string, percent: string, level: BudgetLevel): BudgetStatus {
  return { id: nome, categoryId: `c-${nome}`, categoryName: nome, month: null, amount: '500.00', spent: gasto, remaining: '0.00', percent, level };
}

describe('BudgetsPage', () => {
  let fixture: ComponentFixture<BudgetsPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BudgetsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: MatSnackBar, useValue: { open: vi.fn() } }],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BudgetsPage);
    fixture.componentRef.setInput('month', '2026-10');
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function carregar(lista: BudgetStatus[]) {
    await tick();
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/budgets` && r.params.get('month') === '2026-10').flush(lista);
    httpMock.expectOne(`${API}/categories`).flush([]);
    await fixture.whenStable();
  }

  // F14 CA1: verde em OK, amarela em ATENCAO, vermelha em ESTOURADO
  it('cada barra recebe a classe do nível e a largura para em 100%', async () => {
    await carregar([
      orcamento('Lazer', '100.00', '20.00', 'OK'),
      orcamento('Mercado', '400.00', '80.00', 'ATENCAO'),
      orcamento('Transporte', '750.00', '150.00', 'ESTOURADO'),
    ]);

    const itens = Array.from(element.querySelectorAll('.orcamento'));
    const nivel = (i: Element) => Array.from(i.classList).find((c) => c.startsWith('budget--'));
    expect(itens.map(nivel)).toEqual(['budget--ok', 'budget--warn', 'budget--over']);
    const larguras = itens.map((i) => (i.querySelector('.barra__cheia') as HTMLElement).style.width);
    expect(larguras).toEqual(['20%', '80%', '100%']);
    expect(itens[2].textContent).toContain('Estourado');
    expect(itens[1].textContent).toContain('R$ 400,00 de R$ 500,00 · 80,00%');
  });
});
