import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Só abre a rota com sessão; sem ela, manda para /login. */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  // Ao recarregar a página, espera o Supabase ler a sessão salva antes de decidir
  await auth.ready;
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};
