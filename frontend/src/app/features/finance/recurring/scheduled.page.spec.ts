import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ScheduledOccurrence } from '../data/finance.model';
import { ScheduledPage } from './scheduled.page';

const API = `${environment.apiUrl}/finance`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

function ocorrencia(id: string, descricao: string, status: ScheduledOccurrence['status'], tipo: 'INCOME' | 'EXPENSE' = 'INCOME'): ScheduledOccurrence {
  return {
    id, recurringId: `r-${id}`, type: tipo, description: descricao, amount: '100.00', expectedAmount: '100.00',
    accountId: 'c1', accountName: 'Bradesco', scheduledDate: '2026-10-07', date: '2026-10-07', status,
  };
}

describe('ScheduledPage', () => {
  let fixture: ComponentFixture<ScheduledPage>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    snackBar = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [ScheduledPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(true) })) } },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ScheduledPage);
    fixture.componentRef.setInput('month', '2026-10');
    element = fixture.nativeElement;
  });

  afterEach(() => httpMock.verify());

  async function carregar(lista: ScheduledOccurrence[]) {
    await tick();
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/scheduled` && r.params.get('month') === '2026-10').flush(lista);
    // Lista de regras embutida
    httpMock.match(() => true).forEach((r) => r.flush([]));
    await fixture.whenStable();
  }

  const grupos = () => Array.from(element.querySelectorAll('section.grupo')).map((g) => g.getAttribute('aria-label'));

  it('agrupa por situação e soma o que falta receber e pagar', async () => {
    await carregar([
      ocorrencia('a', 'Internet', 'OVERDUE', 'EXPENSE'),
      ocorrencia('b', 'Salário', 'TO_CONFIRM'),
      ocorrencia('c', 'Aluguel recebido', 'EXPECTED'),
      ocorrencia('d', 'Freela', 'CONFIRMED'),
    ]);

    expect(grupos()).toEqual(['Atrasados', 'Para hoje', 'Próximos', 'Resolvidos']);
    expect(element.textContent).toContain('A receber R$ 200,00');
    expect(element.textContent).toContain('A pagar R$ 100,00');
  });

  it('remarcar com data depois da próxima ocorrência mostra o aviso e recarrega', async () => {
    await carregar([ocorrencia('b', 'Salário', 'TO_CONFIRM')]);
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === 'Remarcar')!.click();
    await fixture.whenStable();
    fixture.componentInstance['remarcacao'].setValue({ date: '2026-11-10' });

    const remarcando = fixture.componentInstance.remarcar(ocorrencia('b', 'Salário', 'TO_CONFIRM'));
    const req = httpMock.expectOne(`${API}/scheduled/b/reschedule`);
    expect(req.request.body).toEqual({ date: '2026-11-10' });
    req.flush({ occurrence: ocorrencia('b', 'Salário', 'RESCHEDULED'), warning: 'A nova data passa da próxima ocorrência (09/11/2026).' });
    await remarcando;

    expect(snackBar.open).toHaveBeenCalledWith('A nova data passa da próxima ocorrência (09/11/2026).', 'OK', expect.anything());
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/scheduled`).flush([]);
  });

  it('"Não vou receber" pede confirmação e chama o skip', async () => {
    await carregar([ocorrencia('b', 'Salário', 'TO_CONFIRM')]);

    const cancelando = fixture.componentInstance.cancelar(ocorrencia('b', 'Salário', 'TO_CONFIRM'));
    await tick();
    httpMock.expectOne(`${API}/scheduled/b/skip`).flush(null, { status: 204, statusText: 'No Content' });
    await cancelando;
    await tick();
    httpMock.expectOne((r) => r.url === `${API}/scheduled`).flush([]);
  });
});
