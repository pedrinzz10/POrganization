/**
 * Tipos da API de finanças. Valores em dinheiro chegam e vão como string com 2 casas ("1234.56"),
 * como o backend serializa BigDecimal: nada de float no caminho.
 */

export type Money = string;

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CASH' | 'INVESTMENT';

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Conta corrente',
  SAVINGS: 'Poupança',
  CASH: 'Dinheiro',
  INVESTMENT: 'Investimento',
};

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  initialBalance: Money;
  balance: Money;
  archived: boolean;
}

export interface AccountRequest {
  name: string;
  type: AccountType;
  initialBalance: Money;
}

export type CategoryKind = 'INCOME' | 'EXPENSE';

export interface Category {
  id: string;
  name: string;
  kind: CategoryKind;
  color: string | null;
  icon: string | null;
}

export interface FinanceTag {
  id: string;
  name: string;
}

export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER';

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  INCOME: 'Renda',
  EXPENSE: 'Gasto',
  TRANSFER: 'Transferência',
};

export interface Transaction {
  id: string;
  type: TransactionType;
  amount: Money;
  date: string;
  description: string | null;
  accountId: string | null;
  accountName: string | null;
  categoryId: string | null;
  categoryName: string | null;
  paid: boolean;
  tags: FinanceTag[];
  cardStatementId: string | null;
  purchaseId: string | null;
  installmentNumber: number | null;
  installmentCount: number | null;
  transferGroupId: string | null;
  transferDirection: 'OUT' | 'IN' | null;
  recurringId: string | null;
}

export interface TransactionRequest {
  type: 'INCOME' | 'EXPENSE';
  amount: Money;
  date: string;
  description: string | null;
  accountId: string;
  categoryId: string;
  paid: boolean;
  tagIds: string[];
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amount: Money;
  date: string;
  description: string | null;
}

export interface MonthSummary {
  month: string;
  income: Money;
  expense: Money;
  net: Money;
}

/** Filtros do extrato; todos opcionais e guardados nos query params da rota. */
export interface TransactionFilters {
  month: string;
  accountId?: string;
  categoryId?: string;
  tagId?: string;
  type?: TransactionType;
}

// ---------- cartões e faturas ----------

export interface Card {
  id: string;
  name: string;
  creditLimit: Money;
  closingDay: number;
  dueDay: number;
  paymentAccountId: string;
  archived: boolean;
}

export interface CardRequest {
  name: string;
  creditLimit: Money;
  closingDay: number;
  dueDay: number;
  paymentAccountId: string;
}

export interface PurchaseRequest {
  amount: Money;
  date: string;
  description: string | null;
  categoryId: string;
  installments: number;
}

export interface PurchaseResponse {
  id: string;
  purchaseId: string;
  transactionIds: string[];
}

/** OPEN (aberta), CLOSED (fechada, aguardando pagamento) ou PAID (paga). */
export type StatementStatus = 'OPEN' | 'CLOSED' | 'PAID';

export const STATEMENT_STATUS_LABELS: Record<StatementStatus, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PAID: 'Paga',
};

export interface StatementSummary {
  id: string;
  cardId: string;
  referenceMonth: string;
  closingDate: string;
  dueDate: string;
  status: StatementStatus;
  total: Money;
}

export interface StatementItem {
  id: string;
  date: string;
  description: string | null;
  amount: Money;
  categoryId: string | null;
  purchaseId: string | null;
  installmentNumber: number | null;
  installmentCount: number | null;
}

/** Fatura de um mês (mês do vencimento). id null = ainda sem compras. */
export interface Statement {
  id: string | null;
  cardId: string;
  referenceMonth: string;
  closingDate: string;
  dueDate: string;
  status: StatementStatus;
  total: Money;
  creditLimit: Money;
  availableLimit: Money;
  paidAt: string | null;
  paymentTransactionId: string | null;
  items: StatementItem[];
}

// ---------- fixos ----------

export interface Recurring {
  id: string;
  type: 'INCOME' | 'EXPENSE';
  amount: Money;
  description: string | null;
  accountId: string | null;
  cardId: string | null;
  categoryId: string;
  dayOfMonth: number;
  startMonth: string;
  endMonth: string | null;
}

/** accountId ou cardId, nunca os dois; cartão só para gasto. */
export interface RecurringRequest {
  type: 'INCOME' | 'EXPENSE';
  amount: Money;
  description: string | null;
  accountId: string | null;
  cardId: string | null;
  categoryId: string;
  dayOfMonth: number;
  startMonth: string;
  endMonth: string | null;
}

// ---------- orçamentos ----------

export type BudgetLevel = 'OK' | 'ATENCAO' | 'ESTOURADO';

export interface BudgetStatus {
  id: string;
  categoryId: string;
  categoryName: string | null;
  /** null = orçamento recorrente (vale todo mês). */
  month: string | null;
  amount: Money;
  spent: Money;
  remaining: Money;
  percent: string;
  level: BudgetLevel;
}

export interface BudgetRequest {
  categoryId: string;
  month: string | null;
  amount: Money;
}

// ---------- metas ----------

export interface Goal {
  id: string;
  name: string;
  targetAmount: Money;
  targetDate: string | null;
  accountId: string | null;
  archived: boolean;
  saved: Money;
  remaining: Money;
  progress: string;
  /** Quanto guardar por mês até o prazo; null sem prazo. */
  monthlyNeeded: Money | null;
  achieved: boolean;
}

export interface GoalRequest {
  name: string;
  targetAmount: Money;
  targetDate: string | null;
  accountId: string | null;
  archived: boolean;
}

export interface Contribution {
  id: string;
  goalId: string;
  amount: Money;
  date: string;
  note: string | null;
}

export interface ContributionRequest {
  amount: Money;
  date: string;
  note: string | null;
}

// ---------- dashboard ----------

export interface CategorySpend {
  categoryId: string;
  name: string | null;
  color: string | null;
  total: Money;
}

export interface OpenStatement {
  id: string;
  referenceMonth: string;
  closingDate: string;
  dueDate: string;
  status: StatementStatus;
  total: Money;
}

export interface CardOverview {
  cardId: string;
  name: string;
  creditLimit: Money;
  availableLimit: Money;
  openStatements: OpenStatement[];
}

export interface MonthPoint {
  month: string;
  income: Money;
  expense: Money;
}

export interface Dashboard {
  month: string;
  totalBalance: Money;
  accounts: { id: string; name: string; balance: Money }[];
  income: Money;
  expense: Money;
  net: Money;
  /** Do maior para o menor. */
  expenseByCategory: CategorySpend[];
  cards: CardOverview[];
  budgetAlerts: BudgetStatus[];
  goals: Goal[];
  /** 6 meses terminando no mês pedido; meses sem movimento vêm com "0.00". */
  lastSixMonths: MonthPoint[];
}
