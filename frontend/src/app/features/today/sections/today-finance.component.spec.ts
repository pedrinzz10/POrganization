import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { FinanceToday } from '../data/today.service';
import { TodayFinanceComponent } from './today-finance.component';

const API = `${environment.apiUrl}/finance`;

// Com consultas pendentes, whenStable() espera elas terminarem; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

function financas(mudancas: Partial<FinanceToday> = {}): FinanceToday {
  return { dueSoon: [], budgetAlerts: [], spentToday: '0.00', toConfirm: [], balances: null, ...mudancas };
}

describe('TodayFinanceComponent', () => {
  let fixture: ComponentFixture<TodayFinanceComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TodayFinanceComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TodayFinanceComponent);
    fixture.componentRef.setInput('today', '2026-10-10');
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function mostrar(f: FinanceToday) {
    fixture.componentRef.setInput('finance', f);
    await fixture.whenStable();
  }

  // F16 T2 (CA2)
  it('orçamento estourado aparece em alerta vermelho (.alert--over) com o nome da categoria', async () => {
    await mostrar(
      financas({
        budgetAlerts: [
          { id: 'b1', categoryId: 'l', categoryName: 'Lazer', month: null, amount: '100.00', spent: '120.00', remaining: '-20.00', percent: '120.00', level: 'ESTOURADO' },
          { id: 'b2', categoryId: 'm', categoryName: 'Mercado', month: null, amount: '500.00', spent: '420.00', remaining: '80.00', percent: '84.00', level: 'ATENCAO' },
        ],
      }),
    );

    const vermelho = element.querySelector('.alert--over')!;
    expect(vermelho.textContent).toContain('Lazer');
    expect(vermelho.textContent).toContain('orçamento estourado');
    expect(element.querySelector('.alert--warn')!.textContent).toContain('Mercado');
    expect(element.querySelectorAll('.alert--over').length).toBe(1);
  });

  it('lista o que vence com "hoje", "amanhã" e "em N dias"', async () => {
    await mostrar(
      financas({
        spentToday: '35.50',
        dueSoon: [
          { kind: 'BILL', id: 't1', title: 'Internet', dueDate: '2026-10-10', amount: '99.90', cardId: null, referenceMonth: null },
          { kind: 'STATEMENT', id: 'f1', title: 'Fatura Nubank', dueDate: '2026-10-12', amount: '450.00', cardId: 'nubank', referenceMonth: '2026-10' },
          { kind: 'BILL', id: 't2', title: 'Luz', dueDate: '2026-10-11', amount: '120.00', cardId: null, referenceMonth: null },
        ],
      }),
    );

    const linhas = Array.from(element.querySelectorAll('.conta')).map((l) =>
      ['.conta__titulo', '.conta__detalhe', '.conta__valor'].map((c) => l.querySelector(c)!.textContent!.trim()),
    );
    expect(linhas).toEqual([
      ['Internet', 'vence hoje', 'R$ 99,90'],
      ['Fatura Nubank', 'vence em 2 dias', 'R$ 450,00'],
      ['Luz', 'vence amanhã', 'R$ 120,00'],
    ]);
    expect(element.querySelector('a[href="/financas/cartoes/nubank/faturas/2026-10"]')).not.toBeNull();
    expect(element.textContent).toContain('R$ 35,50');
  });

  it('lançar gasto abre o formulário compacto só depois de buscar contas e categorias', async () => {
    await mostrar(financas());
    expect(element.querySelector('app-transaction-form')).toBeNull();

    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Lançar gasto'))!.click();
    await tick();
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/accounts`).flush([
      { id: 'c1', name: 'Corrente', type: 'CHECKING', initialBalance: '0.00', balance: '0.00', archived: false },
    ]);
    httpMock.expectOne(`${API}/categories`).flush([{ id: 'lazer', name: 'Lazer', kind: 'EXPENSE', color: null, icon: null }]);
    await fixture.whenStable();

    const form = element.querySelector('app-transaction-form')!;
    expect(form).not.toBeNull();
    // Modo compacto: sem transferência
    expect(form.textContent).not.toContain('Transferência');
  });

  it('sem nada vencendo mostra aviso', async () => {
    await mostrar(financas());
    expect(element.textContent).toContain('Nada vencendo nos próximos dias.');
  });

  // F20 T2 (CA2)
  it('agendado atrasado aparece em "Para confirmar"; "Recebi" confirma e ele sai da lista', async () => {
    const mudou = vi.fn();
    fixture.componentInstance.changed.subscribe(mudou);
    await mostrar(
      financas({
        toConfirm: [
          { id: 'o1', recurringId: 'r1', type: 'INCOME', description: 'Salário', amount: '3200.00', expectedAmount: '3200.00',
            accountId: 'c1', accountName: 'Bradesco', scheduledDate: '2026-10-07', date: '2026-10-07', status: 'OVERDUE' },
        ],
      }),
    );
    const lista = element.querySelector('[aria-label="Para confirmar"]')!;
    expect(lista.textContent).toContain('Salário');
    expect(lista.textContent).toContain('atrasado desde 07/10');

    Array.from(lista.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Recebi'))!.click();
    const req = httpMock.expectOne(`${API}/scheduled/o1/confirm`);
    expect(req.request.method).toBe('POST');
    req.flush({});
    await tick();
    await fixture.whenStable();

    expect(element.querySelector('[aria-label="Para confirmar"]')).toBeNull();
    expect(mudou).toHaveBeenCalled();
  });

  // F21 T3 (CA3)
  it('mostra o saldo de cada conta, o total e o previsto do fim do mês', async () => {
    await mostrar(
      financas({
        balances: {
          accounts: [
            { id: 'b', name: 'Bradesco', balance: '1500.00' },
            { id: 'n', name: 'Nubank', balance: '300.00' },
          ],
          totalBalance: '1800.00', receivable: '3000.00', payable: '1200.00', forecast: '3600.00',
        },
      }),
    );

    const saldos = element.querySelector('[aria-label="Saldo das contas"]')!.textContent!.replace(/\s+/g, ' ');
    expect(saldos).toContain('Bradesco R$ 1.500,00');
    expect(saldos).toContain('Nubank R$ 300,00');
    expect(saldos).toContain('Total R$ 1.800,00');
    expect(saldos).toContain('Previsto no fim do mês R$ 3.600,00');
  });
});
