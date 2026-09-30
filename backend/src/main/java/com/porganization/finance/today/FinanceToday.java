package com.porganization.finance.today;

import com.porganization.finance.budgets.BudgetDtos.BudgetStatus;
import com.porganization.finance.dashboard.ForecastService.Forecast;
import com.porganization.finance.recurring.ScheduledDtos.Occurrence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Seção de finanças da tela Hoje.
 *
 * @param dueSoon      faturas e contas não pagas que vencem hoje ou nos próximos 3 dias
 * @param budgetAlerts orçamentos do mês em ATENCAO ou ESTOURADO
 * @param spentToday   gasto lançado hoje (contas e cartão; pagamento de fatura não conta)
 * @param toConfirm    agendados em conta para confirmar hoje ou atrasados (F20)
 * @param balances     saldo de cada conta ativa, total e previsto do fim do mês (F21)
 */
public record FinanceToday(List<DueItem> dueSoon, List<BudgetStatus> budgetAlerts, BigDecimal spentToday,
        List<Occurrence> toConfirm, Forecast balances) {

    public enum DueKind {
        /** Fatura de cartão (cardId e referenceMonth apontam para ela). */
        STATEMENT,
        /** Gasto não pago numa conta: fixo gerado ou conta lançada como pendente. */
        BILL
    }

    /** id é o da fatura (STATEMENT) ou o da transação (BILL). */
    public record DueItem(DueKind kind, UUID id, String title, LocalDate dueDate, BigDecimal amount, UUID cardId,
            YearMonth referenceMonth) {
    }
}
