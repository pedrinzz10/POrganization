import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Dashboard } from '../data/finance.model';
import { provideFinanceCharts } from './charts/charts.providers';
import { FinanceDashboardPage } from './finance-dashboard.page';

const API = `${environment.apiUrl}/finance`;

// Com consultas pendentes, whenStable() espera elas terminarem; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

const MESES = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];

function painel(mudancas: Partial<Dashboard> = {}): Dashboard {
  return {
    month: '2026-10', totalBalance: '0.00', accounts: [], income: '0.00', expense: '0.00', net: '0.00',
    expenseByCategory: [], cards: [], budgetAlerts: [], goals: [],
    lastSixMonths: MESES.map((month) => ({ month, income: '0.00', expense: '0.00' })),
    ...mudancas,
  };
}

describe('FinanceDashboardPage', () => {
  let fixture: ComponentFixture<FinanceDashboardPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FinanceDashboardPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideFinanceCharts()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FinanceDashboardPage);
    fixture.componentRef.setInput('month', '2026-10');
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function carregar(d: Dashboard) {
    await tick();
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/dashboard` && r.params.get('month') === '2026-10').flush(d);
    await fixture.whenStable();
  }

  // F15 T2 (CA2)
  it('mês sem dados mostra aviso no lugar dos gráficos e nenhum <canvas>', async () => {
    await carregar(painel());

    expect(element.textContent).toContain('Nenhum gasto neste mês');
    expect(element.textContent).toContain('Sem movimento nos últimos 6 meses');
    expect(element.querySelector('canvas')).toBeNull();
  });

  it('com gastos, desenha os gráficos e lista faturas e alertas', async () => {
    await carregar(
      painel({
        totalBalance: '4550.00', income: '5000.00', expense: '950.00', net: '4050.00',
        expenseByCategory: [{ categoryId: 'a', name: 'Alimentação', color: '#F57C00', total: '750.00' }],
        lastSixMonths: MESES.map((month) => ({ month, income: month === '2026-10' ? '5000.00' : '0.00', expense: '0.00' })),
        cards: [{
          cardId: 'nubank', name: 'Nubank', creditLimit: '3000.00', availableLimit: '2700.00',
          openStatements: [{ id: 'f1', referenceMonth: '2026-11', closingDate: '2026-11-05', dueDate: '2026-11-12', status: 'OPEN', total: '100.00' }],
        }],
        budgetAlerts: [{
          id: 'b1', categoryId: 'a', categoryName: 'Alimentação', month: null, amount: '500.00', spent: '750.00',
          remaining: '-250.00', percent: '150.00', level: 'ESTOURADO',
        }],
      }),
    );

    expect(element.querySelectorAll('canvas').length).toBe(2);
    expect(element.textContent).toContain('R$ 4.550,00');
    expect(element.textContent).toContain('Nubank');
    expect(element.textContent).toContain('vence 12/11');
    const alerta = element.querySelector('.alerta')!;
    expect(alerta.classList).toContain('budget--over');
    expect(alerta.textContent).toContain('Estourado');
  });
});
