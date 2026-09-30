import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { GoogleCalendarComponent, navigation } from './google-calendar.component';

const API = `${environment.apiUrl}/integrations/google`;
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('GoogleCalendarComponent', () => {
  let fixture: ComponentFixture<GoogleCalendarComponent>;
  let element: HTMLElement;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GoogleCalendarComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GoogleCalendarComponent);
    element = fixture.nativeElement;
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  async function carregar(status: object) {
    await tick();
    await tick();
    httpMock.expectOne(API).flush(status);
    await fixture.whenStable();
  }

  const botao = (texto: string) =>
    Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(texto));

  it('conectar busca a URL do Google e sai do app para ela', async () => {
    const ir = vi.spyOn(navigation, 'go').mockImplementation(() => undefined);
    await carregar({ available: true, connected: false, email: null, calendarId: null, connectedAt: null });

    botao('Conectar Google Calendar')!.click();
    await tick();
    httpMock.expectOne(`${API}/auth-url`).flush({ url: 'https://accounts.google.com/o/oauth2/v2/auth?state=abc' });
    await tick();

    expect(ir).toHaveBeenCalledWith('https://accounts.google.com/o/oauth2/v2/auth?state=abc');
  });

  it('volta do Google com ?google=ok mostra o resultado e a conta conectada', async () => {
    fixture.componentRef.setInput('result', 'ok');
    await carregar({ available: true, connected: true, email: 'pedro@gmail.com', calendarId: 'primary', connectedAt: '2026-10-01T12:00:00Z' });

    expect(element.textContent).toContain('Google Calendar conectado.');
    expect(element.textContent).toContain('Conectado como pedro@gmail.com');
    expect(botao('Desconectar')).toBeDefined();
  });

  it('?google=erro mostra a falha', async () => {
    fixture.componentRef.setInput('result', 'erro');
    await carregar({ available: true, connected: false, email: null, calendarId: null, connectedAt: null });

    expect(element.querySelector('[role="alert"]')!.textContent).toContain('Não foi possível conectar');
  });

  it('desconectar faz DELETE e recarrega o estado', async () => {
    await carregar({ available: true, connected: true, email: 'pedro@gmail.com', calendarId: 'primary', connectedAt: null });

    botao('Desconectar')!.click();
    await tick();
    httpMock.expectOne({ method: 'DELETE', url: API }).flush(null, { status: 204, statusText: 'No Content' });
    // O reload do resource sai num ciclo seguinte
    await tick();
    await tick();
    httpMock.expectOne(API).flush({ available: true, connected: false, email: null, calendarId: null, connectedAt: null });
    await fixture.whenStable();

    expect(botao('Conectar Google Calendar')).toBeDefined();
  });

  it('servidor sem credenciais do Google avisa, sem botão', async () => {
    await carregar({ available: false, connected: false, email: null, calendarId: null, connectedAt: null });

    expect(element.textContent).toContain('ainda não foi configurada');
    expect(botao('Conectar')).toBeUndefined();
  });
});
