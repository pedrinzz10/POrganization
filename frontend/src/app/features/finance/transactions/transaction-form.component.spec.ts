import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { Account, Category } from '../data/finance.model';
import { TransactionFormComponent } from './transaction-form.component';

const API = `${environment.apiUrl}/finance`;

const CONTAS: Account[] = [
  { id: 'c1', name: 'Corrente', type: 'CHECKING', initialBalance: '0.00', balance: '0.00', archived: false },
  { id: 'c2', name: 'Poupança', type: 'SAVINGS', initialBalance: '0.00', balance: '0.00', archived: false },
];
const CATEGORIAS: Category[] = [
  { id: 'lazer', name: 'Lazer', kind: 'EXPENSE', color: null, icon: null },
  { id: 'salario', name: 'Salário', kind: 'INCOME', color: null, icon: null },
];

describe('TransactionFormComponent', () => {
  let fixture: ComponentFixture<TransactionFormComponent>;
  let httpMock: HttpTestingController;
  let form: TransactionFormComponent['form'];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TransactionFormComponent);
    fixture.componentRef.setInput('accounts', CONTAS);
    fixture.componentRef.setInput('categories', CATEGORIAS);
    await fixture.whenStable();
    form = fixture.componentInstance.form;
  });

  afterEach(() => httpMock.verify());

  it('já escolhe a primeira conta e envia o gasto com o valor em string', async () => {
    const salvo = vi.fn();
    fixture.componentInstance.saved.subscribe(salvo);
    form.patchValue({ amount: '50.00', description: ' Cinema ', categoryId: 'lazer', date: '2026-10-10' });

    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne(`${API}/transactions`);
    expect(req.request.body).toEqual({
      type: 'EXPENSE', amount: '50.00', date: '2026-10-10', description: 'Cinema', accountId: 'c1', categoryId: 'lazer',
      paid: true, tagIds: [],
    });
    req.flush({});
    await salvando;

    expect(salvo).toHaveBeenCalled();
    expect(form.controls.amount.value).toBeNull();
    expect(form.controls.description.value).toBe('');
  });

  it('trocar para renda tira a categoria de gasto escolhida', async () => {
    form.patchValue({ categoryId: 'lazer' });
    form.controls.mode.setValue('INCOME');
    await fixture.whenStable();

    expect(form.controls.categoryId.value).toBeNull();
  });

  it('transferência vai para /transfers com as duas contas', async () => {
    form.patchValue({ mode: 'TRANSFER', amount: '200.00', accountId: 'c1', toAccountId: 'c2', date: '2026-10-10' });

    const salvando = fixture.componentInstance.save();
    const req = httpMock.expectOne(`${API}/transfers`);
    expect(req.request.body).toEqual({ fromAccountId: 'c1', toAccountId: 'c2', amount: '200.00', date: '2026-10-10', description: null });
    req.flush({});
    await salvando;
  });

  it('sem valor ou categoria não chama a API e explica o que falta', async () => {
    await fixture.componentInstance.save();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Informe o valor.');

    form.patchValue({ amount: '10.00' });
    await fixture.componentInstance.save();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Escolha a categoria.');
    httpMock.expectNone(`${API}/transactions`);
  });

  it('no modo compacto não há transferência nem "pago"', async () => {
    fixture.componentRef.setInput('compact', true);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).not.toContain('Transferência');
    expect(fixture.nativeElement.querySelector('mat-checkbox')).toBeNull();
  });
});
