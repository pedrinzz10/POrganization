import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

function rodarGuard(logado: boolean) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { ready: Promise.resolve(), isAuthenticated: signal(logado) } },
    ],
  });
  return TestBed.runInInjectionContext(() =>
    authGuard({} as ActivatedRouteSnapshot, { url: '/hoje' } as RouterStateSnapshot),
  );
}

describe('authGuard', () => {
  // B08 T1 (CA1)
  it('sem sessão, redireciona para /login', async () => {
    const resultado = await rodarGuard(false);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/login');
  });

  it('com sessão, libera a rota', async () => {
    expect(await rodarGuard(true)).toBe(true);
  });
});
