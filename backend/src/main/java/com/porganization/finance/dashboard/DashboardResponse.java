package com.porganization.finance.dashboard;

import com.porganization.finance.budgets.BudgetDtos.BudgetStatus;
import com.porganization.finance.goals.GoalDtos.GoalResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumo financeiro do mês. Cada parte sai do mesmo serviço do endpoint próprio (contas, resumo,
 * faturas, orçamentos, metas), então os números batem com eles.
 */
public record DashboardResponse(
        YearMonth month,
        BigDecimal totalBalance,
        List<AccountBalance> accounts,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        List<CategorySpend> expenseByCategory,
        List<CardOverview> cards,
        List<BudgetStatus> budgetAlerts,
        List<GoalResponse> goals,
        List<MonthPoint> lastSixMonths) {

    public record AccountBalance(UUID id, String name, BigDecimal balance) {
    }

    /** Gasto do mês numa categoria, do maior para o menor. */
    public record CategorySpend(UUID categoryId, String name, String color, BigDecimal total) {
    }

    /** Cartão com o limite disponível e as faturas ainda não pagas que têm compras. */
    public record CardOverview(UUID cardId, String name, BigDecimal creditLimit, BigDecimal availableLimit,
            List<OpenStatement> openStatements) {
    }

    public record OpenStatement(UUID id, YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate, String status,
            BigDecimal total) {
    }

    /** Renda x gasto de um mês da série (meses sem movimento vêm com 0.00). */
    public record MonthPoint(YearMonth month, BigDecimal income, BigDecimal expense) {
    }
}
