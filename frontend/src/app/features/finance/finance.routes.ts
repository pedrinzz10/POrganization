import { Routes } from '@angular/router';
import { provideFinanceCharts } from './dashboard/charts/charts.providers';

/** Telas de finanças, filhas da FinancePage (título + abas). Mês e filtros vão nos query params. */
export const FINANCE_ROUTES: Routes = [
  {
    path: '',
    title: 'Finanças · POrganization',
    // Só o resumo desenha gráficos: o Chart.js é registrado aqui, fora do bundle inicial
    providers: [provideFinanceCharts()],
    loadComponent: () => import('./dashboard/finance-dashboard.page').then((m) => m.FinanceDashboardPage),
  },
  {
    path: 'extrato',
    title: 'Extrato · Finanças · POrganization',
    loadComponent: () => import('./transactions/transactions.page').then((m) => m.TransactionsPage),
  },
  {
    path: 'contas',
    title: 'Contas · Finanças · POrganization',
    loadComponent: () => import('./accounts/accounts.page').then((m) => m.AccountsPage),
  },
  {
    path: 'cartoes',
    title: 'Cartões · Finanças · POrganization',
    loadComponent: () => import('./cards/cards.page').then((m) => m.CardsPage),
  },
  {
    // Fatura que vence no mês :mes ("2026-10"); os parâmetros viram input() da StatementPage
    path: 'cartoes/:id/faturas/:mes',
    title: 'Fatura · Finanças · POrganization',
    loadComponent: () => import('./cards/statement.page').then((m) => m.StatementPage),
  },
  {
    path: 'fixos',
    title: 'Fixos · Finanças · POrganization',
    loadComponent: () => import('./recurring/recurring.page').then((m) => m.RecurringPage),
  },
  {
    path: 'orcamentos',
    title: 'Orçamentos · Finanças · POrganization',
    loadComponent: () => import('./budgets/budgets.page').then((m) => m.BudgetsPage),
  },
  {
    path: 'metas',
    title: 'Metas · Finanças · POrganization',
    loadComponent: () => import('./goals/goals.page').then((m) => m.GoalsPage),
  },
];
