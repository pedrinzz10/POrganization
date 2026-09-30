import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

// O interceptor busca o token de forma assíncrona; espera ele chegar antes de conferir a requisição
const flush = () => new Promise((resolve) => setTimeout(resolve));

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: { freshAccessToken: ReturnType<typeof vi.fn>; signOut: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { freshAccessToken: vi.fn().mockResolvedValue('token-de-teste'), signOut: vi.fn().mockResolvedValue(undefined) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // B08 T2 (CA2)
  it('manda o Bearer só para a API', async () => {
    http.get(`${environment.apiUrl}/me`).subscribe();
    http.get('https://outro.com/dados').subscribe();
    await flush();

    const api = httpMock.expectOne(`${environment.apiUrl}/me`);
    const outro = httpMock.expectOne('https://outro.com/dados');
    expect(api.request.headers.get('Authorization')).toBe('Bearer token-de-teste');
    expect(outro.request.headers.has('Authorization')).toBe(false);
    api.flush({});
    outro.flush({});
  });

  // B08 T2 (CA2): domínio que só começa igual à URL da API não recebe o token
  it('não confunde a API com um domínio parecido', async () => {
    const parecido = `${environment.apiUrl}.evil.test/x`;
    http.get(parecido).subscribe();
    await flush();

    const req = httpMock.expectOne(parecido);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('sem sessão, chama a API sem Authorization', async () => {
    auth.freshAccessToken.mockResolvedValue(null);
    http.get(`${environment.apiUrl}/health`).subscribe();
    await flush();

    const req = httpMock.expectOne(`${environment.apiUrl}/health`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  // B08 T3 (CA3)
  it('401 da API faz logout uma vez e repassa o erro', async () => {
    const resposta = firstValueFrom(http.get(`${environment.apiUrl}/me`));
    await flush();

    httpMock.expectOne(`${environment.apiUrl}/me`).flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(resposta).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(auth.signOut).toHaveBeenCalledTimes(1);
  });

  it('401 de outro domínio não desloga', async () => {
    const resposta = firstValueFrom(http.get('https://outro.com/dados'));
    await flush();

    httpMock.expectOne('https://outro.com/dados').flush({}, { status: 401, statusText: 'Unauthorized' });

    await expect(resposta).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(auth.signOut).not.toHaveBeenCalled();
  });
});
