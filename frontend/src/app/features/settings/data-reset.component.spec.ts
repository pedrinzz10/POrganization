import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { TaskTimerService } from '../tasks/data/task-timer.service';
import { DataResetComponent } from './data-reset.component';

const API = `${environment.apiUrl}/data`;
const tick = (ms = 0) => new Promise((resolve) => setTimeout(resolve, ms));

describe('DataResetComponent', () => {
  let fixture: ComponentFixture<DataResetComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [DataResetComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DataResetComponent);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  const botao = (raiz: ParentNode, texto: string) =>
    Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent!.includes(texto),
    )!;
  const dialogo = () => document.querySelector('mat-dialog-container')!;

  async function abrir(secao: string) {
    botao(element, `Apagar ${secao}`).click();
    await tick(300);
    fixture.detectChanges();
  }

  async function digitar(texto: string) {
    const input = dialogo().querySelector('input')!;
    input.value = texto;
    input.dispatchEvent(new Event('input'));
    await tick();
    fixture.detectChanges();
  }

  // B14 T4 (CA3)
  it('mostra um botão por seção', () => {
    for (const secao of ['compromissos', 'tarefas', 'estudos', 'finanças']) {
      expect(botao(element, `Apagar ${secao}`)).toBeTruthy();
    }
  });

  // B14 T4 (CA3)
  it('só apaga depois de digitar APAGAR, e limpa os cronômetros ao apagar tarefas', async () => {
    const timers = TestBed.inject(TaskTimerService);
    timers.iniciar('t1', '2026-10-06', 'Ler', 10);

    await abrir('tarefas');
    expect(dialogo().textContent).toContain('todas as tarefas diárias');
    const confirmar = botao(dialogo(), 'Apagar');
    expect(confirmar.disabled).toBe(true);

    await digitar('apagar');
    expect(confirmar.disabled).toBe(true);
    await digitar('APAGAR');
    expect(confirmar.disabled).toBe(false);

    confirmar.click();
    await tick(300);
    httpMock.expectOne({ method: 'DELETE', url: `${API}/tasks` }).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await tick();

    expect(timers.timers()).toEqual({});
    expect(document.body.textContent).toContain('Tarefas: dados apagados.');
  });

  // B14 T4 (CA3)
  it('cancelar não chama a API', async () => {
    await abrir('finanças');
    botao(dialogo(), 'Cancelar').click();
    await tick(300);
    httpMock.expectNone(`${API}/finance`);
  });
});
