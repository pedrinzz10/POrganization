import { Routes } from '@angular/router';

// Cada tela é carregada só quando acessada (loadComponent)
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'hoje' },
  {
    path: 'login',
    title: 'Entrar · POrganization',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'cadastro',
    title: 'Criar conta · POrganization',
    loadComponent: () => import('./features/auth/signup/signup.component').then((m) => m.SignupComponent),
  },
  {
    path: 'hoje',
    title: 'Hoje · POrganization',
    loadComponent: () => import('./features/today/today.page').then((m) => m.TodayPage),
  },
];
