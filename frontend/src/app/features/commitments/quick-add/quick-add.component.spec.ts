import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { Commitment } from '../data/commitment.model';
import { QuickAddComponent } from './quick-add.component';

const URL = `${environment.apiUrl}/commitments`;

const criado: Commitment = {
  id: '11111111-1111-4111-8111-111111111111',
  title: 'Dentista',
  date: '2026-10-02',
  startTime: '14:00',
  endTime: null,
  allDay: false,
  description: null,
  location: null,
  done: false,
  recurrenceRule: null,
  createdAt: '2026-10-01T12:00:00Z',
  updatedAt: '2026-10-01T12:00:00Z',
  reminders: [],
};

describe('QuickAddComponent', () => {
  let fixture: ComponentFixture<QuickAddComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuickAddComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(QuickAddComponent);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  const input = (nome: string) => element.querySelector<HTMLInputElement>(`input[formControlName="${nome}"]`)!;

  async function digitar(nome: string, valor: string) {
    input(nome).value = valor;
    input(nome).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function enter() {
    input('title').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();
  }

  it('começa com a data de hoje', () => {
    const hoje = new Date();
    const esperado = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
    expect(input('date').value).toBe(esperado);
  });

  // C06 T1 (CA1)
  it('Enter com título envia title, date e startTime e limpa o título', async () => {
    await digitar('title', 'Dentista');
    await digitar('date', '2026-10-02');
    await digitar('startTime', '14:00');

    await enter();

    const req = httpMock.expectOne({ method: 'POST', url: URL });
    expect(req.request.body).toEqual({ title: 'Dentista', date: '2026-10-02', startTime: '14:00' });
    req.flush(criado, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(input('title').value).toBe('');
    // data e hora ficam para o próximo lançamento
    expect(input('date').value).toBe('2026-10-02');
  });

  // C06 T2 (CA2)
  it('título vazio não envia nada e mostra erro no campo', async () => {
    await digitar('title', '   ');

    await enter();

    httpMock.expectNone({ method: 'POST', url: URL });
    expect(element.querySelector('mat-error')?.textContent).toContain('Informe o título');
  });

  // C06 T3 (CA3)
  it('depois de salvar emite created com o compromisso da API', async () => {
    const recebidos: Commitment[] = [];
    fixture.componentInstance.created.subscribe((c) => recebidos.push(c));
    await digitar('title', 'Dentista');

    await enter();
    httpMock.expectOne({ method: 'POST', url: URL }).flush(criado, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(recebidos).toEqual([criado]);
  });

  it('sem hora, cria de dia todo (não manda startTime)', async () => {
    await digitar('title', 'Aniversário');
    await digitar('startTime', '');

    await enter();

    const req = httpMock.expectOne({ method: 'POST', url: URL });
    expect(req.request.body.startTime).toBeUndefined();
    req.flush(criado, { status: 201, statusText: 'Created' });
  });

  it('erro da API mantém o título para tentar de novo', async () => {
    await digitar('title', 'Dentista');

    await enter();
    httpMock.expectOne({ method: 'POST', url: URL }).flush(
      { title: 'Requisição inválida', errors: [{ field: 'title', message: 'não deve estar em branco' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(input('title').value).toBe('Dentista');
  });
});
