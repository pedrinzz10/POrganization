import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { Card, Category } from '../data/finance.model';
import { CardPurchaseFormComponent } from './card-purchase-form.component';
import { splitInstallments } from './installments';

const API = `${environment.apiUrl}/finance`;

const CARTAO: Card = {
  id: 'nubank', name: 'Nubank', creditLimit: '3000.00', closingDay: 5, dueDay: 12, paymentAccountId: 'c1', archived: false,
};
const CATEGORIAS: Category[] = [
  { id: 'lazer', name: 'Lazer', kind: 'EXPENSE', color: null, icon: null },
  { id: 'salario', name: 'Salário', kind: 'INCOME', color: null, icon: null },
];

describe('CardPurchaseFormComponent', () => {
  let fixture: ComponentFixture<CardPurchaseFormComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardPurchaseFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CardPurchaseFormComponent);
    fixture.componentRef.setInput('card', CARTAO);
    fixture.componentRef.setInput('categories', CATEGORIAS);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const parcelas = () => Array.from(element.querySelectorAll('.parcela')).map((e) => e.textContent!.trim());

  // F13 T1 (CA1)
  it('100,00 em 3x mostra a prévia 33,34 / 33,33 / 33,33 (resto na primeira)', async () => {
    fixture.componentInstance.form.patchValue({ amount: '100.00', installments: 3 });
    await fixture.whenStable();

    expect(parcelas()).toEqual(['1ª R$ 33,34', '2ª R$ 33,33', '3ª R$ 33,33']);
  });

  it('à vista não mostra prévia', async () => {
    fixture.componentInstance.form.patchValue({ amount: '100.00', installments: 1 });
    await fixture.whenStable();

    expect(parcelas()).toEqual([]);
  });

  it('valor pequeno demais para as parcelas avisa e não envia', async () => {
    fixture.componentInstance.form.patchValue({ amount: '0.02', installments: 3, categoryId: 'lazer' });
    await fixture.whenStable();
    expect(element.textContent).toContain('Valor pequeno demais para 3 parcelas');

    await fixture.componentInstance.save();
    httpMock.expectNone(`${API}/cards/nubank/purchases`);
  });

  it('envia a compra com o número de parcelas e só mostra categorias de gasto', async () => {
    const salvo = vi.fn();
    fixture.componentInstance.saved.subscribe(salvo);
    fixture.componentInstance.form.patchValue({
      amount: '100.00', installments: 3, description: 'Loja', categoryId: 'lazer', date: '2026-10-01',
    });

    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne(`${API}/cards/nubank/purchases`);
    expect(req.request.body).toEqual({ amount: '100.00', date: '2026-10-01', description: 'Loja', categoryId: 'lazer', installments: 3 });
    req.flush({ id: 'p1', purchaseId: 'p1', transactionIds: ['a', 'b', 'c'] });
    await salvando;

    expect(salvo).toHaveBeenCalled();
  });
});

describe('splitInstallments', () => {
  it.each([
    ['100.00', 3, ['33.34', '33.33', '33.33']],
    ['10.00', 4, ['2.50', '2.50', '2.50', '2.50']],
    ['0.05', 3, ['0.03', '0.01', '0.01']],
    ['59.90', 1, ['59.90']],
  ])('%s em %ix', (valor, n, esperado) => {
    expect(splitInstallments(valor, n)).toEqual(esperado);
  });

  it('soma das parcelas é sempre o total', () => {
    const parcelas = splitInstallments('1234.57', 7);
    const soma = parcelas.reduce((s, v) => s + Math.round(Number(v) * 100), 0);
    expect(soma).toBe(123457);
  });

  it('valor menor que 1 centavo por parcela ou fora de 1 a 48 → []', () => {
    expect(splitInstallments('0.02', 3)).toEqual([]);
    expect(splitInstallments('100.00', 49)).toEqual([]);
    expect(splitInstallments(null, 2)).toEqual([]);
  });
});
