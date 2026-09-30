package com.porganization.finance.recurring;

import com.porganization.finance.transactions.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ScheduledDtos {

    private ScheduledDtos() {
    }

    /**
     * Uma ocorrência de agendado em conta. id é o do lançamento (null quando cancelada, porque o
     * lançamento sai). scheduledDate é a data da regra; date é a atual (remarcada ou confirmada).
     * amount é o valor do lançamento; expectedAmount, o da regra.
     */
    public record Occurrence(UUID id, UUID recurringId, TransactionType type, String description, BigDecimal amount,
            BigDecimal expectedAmount, UUID accountId, String accountName, LocalDate scheduledDate, LocalDate date,
            OccurrenceStatus status) {
    }

    /** "Recebi"/"Paguei": sem valor, o previsto; sem data, hoje (não pode ser no futuro). */
    public record ConfirmRequest(
            @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            LocalDate date) {
    }

    /** "Não recebi hoje, vou receber em [data]". */
    public record RescheduleRequest(@NotNull LocalDate date) {
    }

    /** warning: a nova data passou da próxima ocorrência da regra (vale, mas é bom saber). */
    public record RescheduleResponse(Occurrence occurrence, String warning) {
    }
}
