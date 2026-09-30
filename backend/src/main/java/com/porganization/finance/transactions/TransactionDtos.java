package com.porganization.finance.transactions;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** Renda ou gasto numa conta. Transferências têm endpoint próprio (F04). */
    public record TransactionRequest(
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 200) String description,
            @NotNull UUID accountId,
            @NotNull UUID categoryId,
            Boolean paid,
            List<UUID> tagIds) {
    }

    public record TagRef(UUID id, String name) {
    }

    public record TransactionResponse(
            UUID id,
            TransactionType type,
            BigDecimal amount,
            LocalDate date,
            String description,
            UUID accountId,
            String accountName,
            UUID categoryId,
            String categoryName,
            boolean paid,
            List<TagRef> tags,
            UUID cardStatementId,
            UUID purchaseId,
            Integer installmentNumber,
            Integer installmentCount,
            UUID transferGroupId,
            TransferDirection transferDirection,
            UUID recurringId) {
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 200) String description) {
    }

    public record TransferResponse(UUID groupId, UUID fromAccountId, UUID toAccountId, BigDecimal amount, LocalDate date,
            String description) {
    }

    /** Renda e gasto do mês (transferências não entram). */
    public record MonthSummary(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }
}
