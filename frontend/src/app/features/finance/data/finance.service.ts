import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Account,
  AccountRequest,
  BudgetRequest,
  BudgetStatus,
  Card,
  CardRequest,
  Category,
  Contribution,
  ContributionRequest,
  FinanceTag,
  Goal,
  GoalRequest,
  MonthSummary,
  PurchaseRequest,
  PurchaseResponse,
  Recurring,
  RecurringRequest,
  Statement,
  StatementSummary,
  Transaction,
  TransactionFilters,
  TransactionRequest,
  TransferRequest,
} from './finance.model';

/** API de finanças (/api/finance). Cada tela usa só o pedaço que precisa. */
@Injectable({ providedIn: 'root' })
export class FinanceService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/finance`;

  // ---------- contas ----------

  listAccounts(includeArchived = false): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.api}/accounts`, { params: { includeArchived } });
  }

  createAccount(request: AccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.api}/accounts`, request);
  }

  updateAccount(id: string, request: AccountRequest): Observable<Account> {
    return this.http.put<Account>(`${this.api}/accounts/${id}`, request);
  }

  setAccountArchived(id: string, archived: boolean): Observable<Account> {
    return this.http.patch<Account>(`${this.api}/accounts/${id}`, { archived });
  }

  deleteAccount(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/accounts/${id}`);
  }

  // ---------- categorias e tags ----------

  listCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.api}/categories`);
  }

  listTags(): Observable<FinanceTag[]> {
    return this.http.get<FinanceTag[]>(`${this.api}/tags`);
  }

  // ---------- transações ----------

  /** Extrato do mês com os filtros que vierem preenchidos. */
  listTransactions(filters: TransactionFilters): Observable<Transaction[]> {
    let params = new HttpParams().set('month', filters.month);
    if (filters.accountId) params = params.set('accountId', filters.accountId);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.tagId) params = params.set('tagId', filters.tagId);
    if (filters.type) params = params.set('type', filters.type);
    return this.http.get<Transaction[]>(`${this.api}/transactions`, { params });
  }

  createTransaction(request: TransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.api}/transactions`, request);
  }

  updateTransaction(id: string, request: TransactionRequest): Observable<Transaction> {
    return this.http.put<Transaction>(`${this.api}/transactions/${id}`, request);
  }

  /** Excluir uma perna de transferência exclui as duas. */
  deleteTransaction(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/transactions/${id}`);
  }

  createTransfer(request: TransferRequest): Observable<unknown> {
    return this.http.post(`${this.api}/transfers`, request);
  }

  summary(month: string): Observable<MonthSummary> {
    return this.http.get<MonthSummary>(`${this.api}/summary`, { params: { month } });
  }

  // ---------- cartões e faturas ----------

  listCards(): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.api}/cards`);
  }

  createCard(request: CardRequest): Observable<Card> {
    return this.http.post<Card>(`${this.api}/cards`, request);
  }

  updateCard(id: string, request: CardRequest): Observable<Card> {
    return this.http.put<Card>(`${this.api}/cards/${id}`, request);
  }

  /** Compra à vista ou parcelada: cada parcela cai numa fatura seguida. */
  purchase(cardId: string, request: PurchaseRequest): Observable<PurchaseResponse> {
    return this.http.post<PurchaseResponse>(`${this.api}/cards/${cardId}/purchases`, request);
  }

  /** Exclui a compra com todas as parcelas. */
  deletePurchase(purchaseId: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/cards/purchases/${purchaseId}`);
  }

  listStatements(cardId: string): Observable<StatementSummary[]> {
    return this.http.get<StatementSummary[]>(`${this.api}/cards/${cardId}/statements`);
  }

  /** Fatura que vence no mês ("2026-10"), com itens e limite disponível. */
  statement(cardId: string, month: string): Observable<Statement> {
    return this.http.get<Statement>(`${this.api}/cards/${cardId}/statements`, { params: { month } });
  }

  payStatement(statementId: string): Observable<Statement> {
    return this.http.post<Statement>(`${this.api}/cards/statements/${statementId}/pay`, {});
  }

  // ---------- fixos ----------

  listRecurring(): Observable<Recurring[]> {
    return this.http.get<Recurring[]>(`${this.api}/recurring`);
  }

  createRecurring(request: RecurringRequest): Observable<Recurring> {
    return this.http.post<Recurring>(`${this.api}/recurring`, request);
  }

  updateRecurring(id: string, request: RecurringRequest): Observable<Recurring> {
    return this.http.put<Recurring>(`${this.api}/recurring/${id}`, request);
  }

  /** Os lançamentos já gerados ficam; só o modelo sai. */
  deleteRecurring(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/recurring/${id}`);
  }

  // ---------- orçamentos ----------

  budgets(month: string): Observable<BudgetStatus[]> {
    return this.http.get<BudgetStatus[]>(`${this.api}/budgets`, { params: { month } });
  }

  createBudget(request: BudgetRequest): Observable<BudgetStatus> {
    return this.http.post<BudgetStatus>(`${this.api}/budgets`, request);
  }

  updateBudget(id: string, amount: string): Observable<BudgetStatus> {
    return this.http.put<BudgetStatus>(`${this.api}/budgets/${id}`, { amount });
  }

  deleteBudget(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/budgets/${id}`);
  }

  // ---------- metas ----------

  listGoals(): Observable<Goal[]> {
    return this.http.get<Goal[]>(`${this.api}/goals`);
  }

  getGoal(id: string): Observable<Goal> {
    return this.http.get<Goal>(`${this.api}/goals/${id}`);
  }

  createGoal(request: GoalRequest): Observable<Goal> {
    return this.http.post<Goal>(`${this.api}/goals`, request);
  }

  updateGoal(id: string, request: GoalRequest): Observable<Goal> {
    return this.http.put<Goal>(`${this.api}/goals/${id}`, request);
  }

  deleteGoal(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/goals/${id}`);
  }

  contribute(goalId: string, request: ContributionRequest): Observable<Contribution> {
    return this.http.post<Contribution>(`${this.api}/goals/${goalId}/contributions`, request);
  }
}
