package com.porganization.finance.goals;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
            LocalDate targetDate,
            UUID accountId,
            Boolean archived) {
    }

    /**
     * Meta com progresso. progress é o percentual guardado (truncado em 2 casas, pode passar de 100);
     * monthlyNeeded é quanto guardar por mês até o prazo (null sem prazo; prazo vencido = tudo o que falta).
     */
    public record GoalResponse(UUID id, String name, BigDecimal targetAmount, LocalDate targetDate, UUID accountId,
            boolean archived, BigDecimal saved, BigDecimal remaining, BigDecimal progress, BigDecimal monthlyNeeded,
            boolean achieved) {
    }

    public record ContributionRequest(
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 200) String note) {
    }

    public record ContributionResponse(UUID id, UUID goalId, BigDecimal amount, LocalDate date, String note) {
    }
}
