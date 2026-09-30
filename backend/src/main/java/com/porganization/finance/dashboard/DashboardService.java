package com.porganization.finance.dashboard;

import com.porganization.finance.budgets.BudgetDtos.BudgetLevel;
import com.porganization.finance.budgets.BudgetService;
import com.porganization.finance.dashboard.ForecastService.Forecast;
import com.porganization.finance.cards.CardDtos.CardResponse;
import com.porganization.finance.cards.CreditCardService;
import com.porganization.finance.cards.StatementService;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.dashboard.DashboardResponse.CardOverview;
import com.porganization.finance.dashboard.DashboardResponse.CategorySpend;
import com.porganization.finance.dashboard.DashboardResponse.MonthPoint;
import com.porganization.finance.dashboard.DashboardResponse.OpenStatement;
import com.porganization.finance.goals.GoalService;
import com.porganization.finance.transactions.CategoryTotal;
import com.porganization.finance.transactions.TransactionDtos.MonthSummary;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.finance.transactions.TransactionService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int SERIES_MONTHS = 6;

    private final ForecastService forecasts;
    private final TransactionService transactionService;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final CreditCardService cards;
    private final StatementService statements;
    private final BudgetService budgets;
    private final GoalService goals;

    public DashboardService(ForecastService forecasts, TransactionService transactionService, TransactionRepository transactions,
            CategoryRepository categories, CreditCardService cards, StatementService statements, BudgetService budgets,
            GoalService goals) {
        this.forecasts = forecasts;
        this.transactionService = transactionService;
        this.transactions = transactions;
        this.categories = categories;
        this.cards = cards;
        this.statements = statements;
        this.budgets = budgets;
        this.goals = goals;
    }

    @Transactional(readOnly = true)
    public DashboardResponse of(UUID userId, YearMonth month) {
        Forecast forecast = forecasts.of(userId, month);
        MonthSummary summary = transactionService.summary(userId, month);

        return new DashboardResponse(
                month,
                forecast.totalBalance(),
                forecast.accounts(),
                summary.income(),
                summary.expense(),
                summary.net(),
                expenseByCategory(userId, month),
                cards(userId),
                budgets.status(userId, month).stream().filter(b -> b.level() != BudgetLevel.OK).toList(),
                goals.list(userId).stream().filter(g -> !g.archived()).toList(),
                series(userId, month),
                forecast.receivable(),
                forecast.payable(),
                forecast.forecast());
    }

    private List<CategorySpend> expenseByCategory(UUID userId, YearMonth month) {
        Map<UUID, Category> byId = categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        return transactions.expenseByCategory(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(t -> spend(t, byId.get(t.categoryId())))
                .sorted(Comparator.comparing(CategorySpend::total).reversed())
                .toList();
    }

    private static CategorySpend spend(CategoryTotal t, Category category) {
        return new CategorySpend(t.categoryId(), category == null ? null : category.getName(),
                category == null ? null : category.getColor(), t.total());
    }

    private List<CardOverview> cards(UUID userId) {
        List<CardOverview> result = new ArrayList<>();
        for (CardResponse card : cards.list(userId)) {
            if (card.archived()) {
                continue;
            }
            List<OpenStatement> open = cards.statements(userId, card.id()).stream()
                    .filter(s -> !"PAID".equals(s.status()) && s.total().signum() > 0)
                    .map(s -> new OpenStatement(s.id(), s.referenceMonth(), s.closingDate(), s.dueDate(), s.status(), s.total()))
                    .toList();
            result.add(new CardOverview(card.id(), card.name(), card.creditLimit(),
                    statements.availableLimit(userId, cards.find(userId, card.id())), open));
        }
        return result;
    }

    /** Os 6 meses terminando no mês pedido, do mais antigo para o mais novo. */
    private List<MonthPoint> series(UUID userId, YearMonth month) {
        List<MonthPoint> points = new ArrayList<>();
        for (int i = SERIES_MONTHS - 1; i >= 0; i--) {
            MonthSummary s = transactionService.summary(userId, month.minusMonths(i));
            points.add(new MonthPoint(month.minusMonths(i), s.income(), s.expense()));
        }
        return points;
    }
}
