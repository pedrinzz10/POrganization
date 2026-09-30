package com.porganization.finance.budgets;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    /** month vazio = orçamento recorrente (todo mês). */
    public record BudgetRequest(
            @NotNull UUID categoryId,
            YearMonth month,
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record BudgetAmountRequest(
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    /** Até 80% OK, de 80% até antes de 100% ATENCAO, a partir de 100% ESTOURADO. */
    public enum BudgetLevel {
        OK,
        ATENCAO,
        ESTOURADO
    }

    /**
     * Orçamento que vale no mês com o realizado. month null = veio do recorrente.
     * percent é truncado em 2 casas (79.998% aparece 79.99%, coerente com o nível OK).
     */
    public record BudgetStatus(UUID id, UUID categoryId, String categoryName, YearMonth month, BigDecimal amount,
            BigDecimal spent, BigDecimal remaining, BigDecimal percent, BudgetLevel level) {
    }
}
