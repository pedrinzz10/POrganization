package com.porganization.finance.transactions;

import java.math.BigDecimal;
import java.util.UUID;

/** Soma dos gastos de uma categoria num período (orçamentos e dashboard). */
public record CategoryTotal(UUID categoryId, BigDecimal total) {
}
