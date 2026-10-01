import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Statement } from '../data/finance.model';
import { StatementPage } from './statement.page';

const API = `${environment.apiUrl}/finance`;

// Com consultas pendentes, whenStable() espera elas terminarem; só deixamos a fila andar
const tick = () => new Promise((resolve) => setTimeout(resolve));

function fatura(mudancas: Partial<Statement> = {}): Statement {
  return {
    id: 'f1', cardId: 'nubank', referenceMonth: '2026-10', closingDate: '2026-10-05', dueDate: '2026-10-12',
    status: 'CLOSED', total: '450.00', creditLimit: '3000.00', availableLimit: '2550.00', paidAt: null,
    paymentTransactionId: null,
    items: [{ id: 't1', date: '2026-10-01', description: 'Mercado', amount: '450.00', categoryId: 'c', purchaseId: 'p1', installmentNumber: 1, installmentCount: 1, recurringId: null }],
    ...mudancas,
  };
}

describe('StatementPage', () => {
  let fixture: ComponentFixture<StatementPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let confirmar: boolean;

  beforeEach(async () => {
    confirmar = true;
    dialog = { open: vi.fn(() => ({ afterClosed: () => of(confirmar) })) };
    await TestBed.configureTestingModule({
      imports: [StatementPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StatementPage);
    fixture.componentRef.setInput('id', 'nubank');
    fixture.componentRef.setInput('mes', '2026-10');
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function carregar(f: Statement) {
    await tick();
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/cards/nubank/statements` && r.params.get('month') === '2026-10').flush(f);
    httpMock.expectOne(`${API}/cards`).flush([{ id: 'nubank', name: 'Nubank' }]);
    await fixture.whenStable();
  }

  const selo = () => element.querySelector('[class*="selo--"]')!.textContent!.trim();
  const botaoPagar = () =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === 'Pagar');

  // F13 T2 (CA2)
  it('fatura fechada: Pagar pede confirmação, chama /pay e o selo vira "Paga"', async () => {
    await carregar(fatura());
    expect(selo()).toBe('Fechada');
    expect(element.textContent).toContain('Nubank');

    botaoPagar()!.click();
    await tick();

    expect(dialog.open).toHaveBeenCalled();
    const req = httpMock.expectOne(`${API}/cards/statements/f1/pay`);
    expect(req.request.method).toBe('POST');
    req.flush(fatura({ status: 'PAID', paidAt: '2026-10-10T12:00:00Z', availableLimit: '3000.00' }));
    await tick();
    await fixture.whenStable();

    expect(selo()).toBe('Paga');
    expect(botaoPagar()).toBeUndefined();
    expect(element.textContent).toContain('R$ 3.000,00');
  });

  it('cancelar a confirmação não paga', async () => {
    confirmar = false;
    await carregar(fatura());

    botaoPagar()!.click();
    await tick();

    httpMock.expectNone(`${API}/cards/statements/f1/pay`);
    expect(selo()).toBe('Fechada');
  });

  it('fatura vazia (sem id) não tem botão Pagar', async () => {
    await carregar(fatura({ id: null, status: 'OPEN', total: '0.00', items: [] }));

    expect(selo()).toBe('Aberta');
    expect(botaoPagar()).toBeUndefined();
    expect(element.textContent).toContain('Nenhuma compra nesta fatura.');
  });
});
