package com.porganization.finance.transactions;

public enum TransactionType {
    INCOME,
    EXPENSE,
    /** Perna de uma transferência entre contas (F04): não conta como renda nem gasto. */
    TRANSFER
}
