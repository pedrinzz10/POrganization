import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

// Compara com a barra no fim: "https://api.x/api.evil.test" não é a nossa API
const isApiRequest = (url: string) => url === environment.apiUrl || url.startsWith(`${environment.apiUrl}/`);

/**
 * Toda requisição do HttpClient para a nossa API leva "Authorization: Bearer <token>".
 * Outros domínios nunca recebem o token. Um 401 da API encerra a sessão (volta ao login).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url)) {
    return next(req);
  }
  const auth = inject(AuthService);

  return from(auth.freshAccessToken()).pipe(
    switchMap((token) =>
      next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req),
    ),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        void auth.signOut();
      }
      return throwError(() => error);
    }),
  );
};
