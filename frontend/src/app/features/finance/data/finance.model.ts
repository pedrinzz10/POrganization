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
