import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

describe('rotas do app', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: {
            ready: Promise.resolve(),
            isAuthenticated: signal(true),
            user: signal({ email: 'pedro@teste.com' }),
            signOut: vi.fn(),
          },
        },
      ],
    });
  });

  // B09 T3 (CA3)
  it('a raiz / redireciona para /hoje', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(TestBed.inject(Router).url).toBe('/hoje');
  });

  it('rota desconhecida volta para /hoje', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/nao-existe');
    expect(TestBed.inject(Router).url).toBe('/hoje');
  });
});
