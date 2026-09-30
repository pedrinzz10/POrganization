package com.porganization.finance.recurring;

import com.porganization.finance.transactions.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public final class RecurringDtos {

    private RecurringDtos() {
    }

    /** accountId ou cardId, nunca os dois; cartão só para gasto. endMonth vazio = sem fim. */
    public record RecurringRequest(
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @Size(max = 200) String description,
            UUID accountId,
            UUID cardId,
            @NotNull UUID categoryId,
            @NotNull @Min(1) @Max(31) Integer dayOfMonth,
            @NotNull YearMonth startMonth,
            YearMonth endMonth) {
    }

    public record RecurringResponse(UUID id, TransactionType type, BigDecimal amount, String description, UUID accountId,
            UUID cardId, UUID categoryId, int dayOfMonth, YearMonth startMonth, YearMonth endMonth) {
    }

    /** Quantas transações a geração do mês criou agora (0 se o mês já estava gerado). */
    public record GenerateResponse(YearMonth month, int created) {
    }
}
