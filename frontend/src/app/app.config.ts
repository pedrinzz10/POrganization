import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { provideLocale } from './core/locale';
import { ThemeService } from './core/theme/theme.service';

// Tudo que o app precisa é declarado aqui, num único lugar, sem NgModule
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideLocale(),
    // Service worker (só no build de produção): recebe as notificações push com o app fechado
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    // <mat-icon> usa a fonte Material Symbols carregada no index.html
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
    }),
    // Aplica o tema claro/escuro escolhido antes da primeira tela
    provideAppInitializer(() => {
      inject(ThemeService);
    }),
  ],
};
