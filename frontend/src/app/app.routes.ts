import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

// Cada tela é carregada só quando acessada (loadComponent). As seções logadas são filhas
// do ShellComponent (barra + menu), e o guard protege todas de uma vez.
export const routes: Routes = [
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
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'hoje' },
      {
        path: 'hoje',
        title: 'Hoje · POrganization',
        loadComponent: () => import('./features/today/today.page').then((m) => m.TodayPage),
      },
      {
        path: 'compromissos',
        title: 'Compromissos · POrganization',
        loadComponent: () => import('./features/commitments/commitments.page').then((m) => m.CommitmentsPage),
      },
      {
        path: 'estudos',
        title: 'Estudos · POrganization',
        loadComponent: () => import('./features/studies/studies.page').then((m) => m.StudiesPage),
      },
      {
        path: 'financas',
        title: 'Finanças · POrganization',
        loadComponent: () => import('./features/finance/finance.page').then((m) => m.FinancePage),
        loadChildren: () => import('./features/finance/finance.routes').then((m) => m.FINANCE_ROUTES),
      },
      {
        path: 'configuracoes',
        title: 'Configurações · POrganization',
        loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage),
      },
    ],
  },
  { path: '**', redirectTo: 'hoje' },
];
