import { Routes } from '@angular/router';

/** Telas de finanças, filhas da FinancePage (título + abas). Mês e filtros vão nos query params. */
export const FINANCE_ROUTES: Routes = [
  {
    path: '',
    title: 'Finanças · POrganization',
    loadComponent: () => import('./transactions/transactions.page').then((m) => m.TransactionsPage),
  },
  {
    path: 'contas',
    title: 'Contas · Finanças · POrganization',
    loadComponent: () => import('./accounts/accounts.page').then((m) => m.AccountsPage),
  },
];
