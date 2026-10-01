import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { environment } from '../../../../environments/environment';
import { RecurringFormDialog } from './recurring-form.dialog';

const API = `${environment.apiUrl}/finance`;
const tick = (ms = 0) => new Promise((resolve) => setTimeout(resolve, ms));

describe('RecurringFormDialog (regra de data)', () => {
  let fixture: ComponentFixture<RecurringFormDialog>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [RecurringFormDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: {} },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecurringFormDialog);
    element = fixture.nativeElement;
    await tick();
    httpMock
      .expectOne(`${API}/accounts?includeArchived=false`)
      .flush([
        {
          id: 'c1',
          name: 'Bradesco',
          type: 'CHECKING',
          initialBalance: '0.00',
          balance: '0.00',
          archived: false,
        },
      ]);
    httpMock.expectOne(`${API}/cards`).flush([]);
    httpMock
      .expectOne(`${API}/categories`)
      .flush([{ id: 'sal', name: 'Salário', kind: 'INCOME', color: null, icon: null }]);
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  /** A prévia sai 300 ms depois da última mudança. */
  async function responderPrevia(corpoEsperado: object, datas: string[]) {
    await tick(350);
    const reqs = httpMock.match(`${API}/recurring/preview`);
    expect(reqs.at(-1)!.request.body).toEqual(corpoEsperado);
    reqs.forEach((r) => r.flush({ nextDates: datas }));
    await fixture.whenStable();
  }

  // F20 T1 (CA1)
  it('"N-ésimo dia útil" troca os campos, mostra as próximas datas e manda a regra no POST', async () => {
    await responderPrevia(
      { ruleType: 'DAY_OF_MONTH', dayOfMonth: 10, businessDay: null, adjustment: 'KEEP' },
      ['2026-10-10'],
    );
    expect(element.textContent).toContain('Dia do mês');

    const form = fixture.componentInstance.form;
    form.patchValue({ type: 'INCOME', ruleType: 'BUSINESS_DAY', businessDay: 5 });
    await fixture.whenStable();
    await responderPrevia(
      { ruleType: 'BUSINESS_DAY', dayOfMonth: null, businessDay: 5, adjustment: 'KEEP' },
      ['2026-10-07', '2026-11-09', '2026-12-07'],
    );

    expect(element.textContent).not.toContain('Dia do mês');
    expect(element.textContent).toContain('Qual dia útil');
    expect(element.textContent).toContain('Próximas datas: 07/10/2026 · 09/11/2026 · 07/12/2026');

    form.patchValue({
      amount: '3200.00',
      description: 'Salário',
      categoryId: 'sal',
      target: 'conta:c1',
      startMonth: '2026-10',
    });
    const salvando = fixture.componentInstance.save();
    const post = httpMock.expectOne({ method: 'POST', url: `${API}/recurring` });
    expect(post.request.body).toMatchObject({
      ruleType: 'BUSINESS_DAY',
      businessDay: 5,
      dayOfMonth: null,
      accountId: 'c1',
    });
    post.flush({});
    await salvando;
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('a validação segue a regra: sem o dia útil não salva; o dia do mês não é exigido', async () => {
    await responderPrevia(
      { ruleType: 'DAY_OF_MONTH', dayOfMonth: 10, businessDay: null, adjustment: 'KEEP' },
      [],
    );
    const form = fixture.componentInstance.form;
    form.patchValue({
      ruleType: 'BUSINESS_DAY',
      businessDay: null,
      dayOfMonth: null,
      amount: '10.00',
      categoryId: 'sal',
      target: 'conta:c1',
    });
    await fixture.whenStable();

    expect(form.controls.businessDay.invalid).toBe(true);
    expect(form.controls.dayOfMonth.valid).toBe(true);
    await fixture.componentInstance.save();
    httpMock.expectNone({ method: 'POST', url: `${API}/recurring` });
    await tick(350);
    // Com o campo da regra inválido, nem pede prévia
    httpMock.expectNone(`${API}/recurring/preview`);
  });
});

// F24 T2 (CA1)
describe('RecurringFormDialog (assinatura no cartão)', () => {
  it('aberto pelo cartão: título "Nova assinatura", sem Renda, cartão escolhido e cardId no POST', async () => {
    const dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [RecurringFormDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { cardId: 'nubank' } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RecurringFormDialog);
    const element: HTMLElement = fixture.nativeElement;
    await tick();
    httpMock.expectOne(`${API}/accounts?includeArchived=false`).flush([]);
    httpMock
      .expectOne(`${API}/cards`)
      .flush([
        {
          id: 'nubank',
          name: 'Nubank',
          creditLimit: '3000.00',
          closingDay: 5,
          dueDay: 12,
          paymentAccountId: 'c1',
          archived: false,
        },
      ]);
    httpMock
      .expectOne(`${API}/categories`)
      .flush([{ id: 'lazer', name: 'Lazer', kind: 'EXPENSE', color: null, icon: null }]);
    await fixture.whenStable();

    expect(element.textContent).toContain('Nova assinatura');
    expect(element.textContent).not.toContain('Renda');
    const form = fixture.componentInstance.form;
    // F25 T2: assinatura não tem dia de pagamento
    expect(element.textContent).not.toContain('Quando');
    expect(element.textContent).not.toContain('Dia do mês');
    expect(element.textContent).toContain('Entra em toda fatura do cartão');

    form.patchValue({ amount: '39.90', description: 'Netflix', categoryId: 'lazer' });
    await tick(350);
    httpMock.match(`${API}/recurring/preview`).forEach((r) => r.flush({ nextDates: [] }));
    const salvando = fixture.componentInstance.save();
    const post = httpMock.expectOne({ method: 'POST', url: `${API}/recurring` });
    expect(post.request.body).toMatchObject({
      type: 'EXPENSE',
      cardId: 'nubank',
      accountId: null,
      description: 'Netflix',
      ruleType: 'DAY_OF_MONTH',
      dayOfMonth: 1,
    });
    post.flush({});
    await salvando;
    expect(dialogRef.close).toHaveBeenCalledWith(true);
    httpMock.verify();
  });
});
