import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CardsPage } from './cards.page';

const API = `${environment.apiUrl}/finance`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

const CARTAO = {
  id: 'nubank',
  name: 'Nubank',
  creditLimit: '3000.00',
  closingDay: 5,
  dueDay: 12,
  paymentAccountId: 'c1',
  archived: false,
};

function assinatura(
  id: string,
  description: string,
  amount: string,
  nextDate: string | null,
  cardId: string | null = 'nubank',
) {
  return {
    id,
    type: 'EXPENSE',
    amount,
    description,
    accountId: cardId ? null : 'c1',
    cardId,
    categoryId: 'lazer',
    ruleType: 'DAY_OF_MONTH',
    dayOfMonth: 7,
    businessDay: null,
    adjustment: 'KEEP',
    startMonth: '2026-01',
    endMonth: null,
    nextDate,
  };
}

describe('CardsPage (assinaturas)', () => {
  let fixture: ComponentFixture<CardsPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let dialog: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialog = { open: vi.fn(() => ({ afterClosed: () => of(false) })) };
    await TestBed.configureTestingModule({
      imports: [CardsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CardsPage);
    element = fixture.nativeElement;
    await tick();
    await tick();
    httpMock.expectOne(`${API}/cards`).flush([CARTAO]);
    httpMock.expectOne(`${API}/categories`).flush([]);
    httpMock
      .expectOne(`${API}/recurring`)
      .flush([
        assinatura('n', 'Netflix', '39.90', '2026-11-07'),
        assinatura('s', 'Spotify', '21.90', '2026-11-07'),
        assinatura('fim', 'Antiga', '10.00', null),
        assinatura('aluguel', 'Aluguel', '1500.00', '2026-11-07', null),
      ]);
    await tick();
    httpMock
      .match((r) => r.url === `${API}/cards/nubank/statements`)
      .forEach((r) =>
        r.flush({
          id: null,
          cardId: 'nubank',
          referenceMonth: '2026-11',
          closingDate: '2026-11-05',
          dueDate: '2026-11-12',
          status: 'OPEN',
          total: '0.00',
          creditLimit: '3000.00',
          availableLimit: '3000.00',
          paidAt: null,
          paymentTransactionId: null,
          items: [],
        }),
      );
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  // F24 T3 (CA2)
  it('mostra as assinaturas ativas do cartão com o total por mês', () => {
    const bloco = element.querySelector('[aria-label="Assinaturas do Nubank"]')!;
    expect(bloco.textContent).toContain('Netflix');
    expect(bloco.textContent).toContain('Spotify');
    // F25: a próxima cobrança (07/11, depois do fechamento dia 5) entra na fatura de dezembro
    expect(bloco.textContent).toContain('próxima: fatura de dezembro de 2026');
    expect(bloco.textContent).toContain('61,80/mês');
    expect(bloco.textContent).not.toContain('Antiga'); // já terminou
    expect(bloco.textContent).not.toContain('Aluguel'); // é da conta
  });

  it('"Nova assinatura" abre o formulário já com o cartão', () => {
    (
      Array.from(element.querySelectorAll('button')).find((b) =>
        b.textContent!.includes('Nova assinatura'),
      ) as HTMLElement
    ).click();
    expect(dialog.open).toHaveBeenCalledWith(expect.anything(), { data: { cardId: 'nubank' } });
  });
});
