package com.porganization.finance.recurring;

import com.porganization.finance.calendar.BusinessCalendar.Adjustment;
import com.porganization.finance.transactions.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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
            // Regra da data: sem ruleType, DAY_OF_MONTH (o formato da F08 continua valendo)
            ScheduleRule ruleType,
            @Min(1) @Max(31) Integer dayOfMonth,
            @Min(1) @Max(15) Integer businessDay,
            Adjustment adjustment,
            @NotNull YearMonth startMonth,
            YearMonth endMonth) {

        public ScheduleRule rule() {
            return ruleType == null ? ScheduleRule.DAY_OF_MONTH : ruleType;
        }
    }

    /** nextDate: a próxima ocorrência a partir de hoje, pela regra (null se o agendado já terminou). */
    public record RecurringResponse(UUID id, TransactionType type, BigDecimal amount, String description, UUID accountId,
            UUID cardId, UUID categoryId, ScheduleRule ruleType, Integer dayOfMonth, Integer businessDay, Adjustment adjustment,
            YearMonth startMonth, YearMonth endMonth, LocalDate nextDate) {
    }

    /** Prévia das próximas datas de uma regra, para o formulário mostrar antes de salvar. */
    public record RulePreviewRequest(
            ScheduleRule ruleType,
            @Min(1) @Max(31) Integer dayOfMonth,
            @Min(1) @Max(15) Integer businessDay,
            Adjustment adjustment) {
    }

    public record RulePreviewResponse(List<LocalDate> nextDates) {
    }

    /** Quantas transações a geração do mês criou agora (0 se o mês já estava gerado). */
    public record GenerateResponse(YearMonth month, int created) {
    }
}
