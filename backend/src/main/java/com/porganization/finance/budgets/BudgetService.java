package com.porganization.finance.budgets;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.budgets.BudgetDtos.BudgetLevel;
import com.porganization.finance.budgets.BudgetDtos.BudgetRequest;
import com.porganization.finance.budgets.BudgetDtos.BudgetStatus;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryKind;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.transactions.CategoryTotal;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.settings.UserSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ALERT_PERCENT = BigDecimal.valueOf(80);

    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public BudgetService(BudgetRepository budgets, CategoryRepository categories, TransactionRepository transactions,
            UserSettingsService userSettings, Clock clock) {
        this.budgets = budgets;
        this.categories = categories;
        this.transactions = transactions;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    /** Orçamentos que valem no mês (o do mês no lugar do recorrente) com gasto, percentual e nível. */
    @Transactional(readOnly = true)
    public List<BudgetStatus> status(UUID userId, YearMonth month) {
        Map<UUID, Budget> effective = new LinkedHashMap<>();
        for (Budget b : budgets.candidatesFor(userId, month.atDay(1))) {
            effective.merge(b.getCategoryId(), b, (current, other) -> current.getMonth() != null ? current : other);
        }
        Map<UUID, BigDecimal> spent = transactions.expenseByCategory(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.toMap(CategoryTotal::categoryId, CategoryTotal::total));
        Map<UUID, String> names = categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        return effective.values().stream()
                .map(b -> {
                    BigDecimal s = spent.getOrDefault(b.getCategoryId(), BigDecimal.ZERO.setScale(2));
                    return new BudgetStatus(b.getId(), b.getCategoryId(), names.get(b.getCategoryId()), b.getMonth(),
                            b.getAmount(), s, b.getAmount().subtract(s), percent(b.getAmount(), s), level(b.getAmount(), s));
                })
                .sorted(Comparator.comparing(BudgetStatus::categoryName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public BudgetStatus create(UUID userId, BudgetRequest request) {
        Category category = categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw new InvalidRequestException("categoryId", "orçamento é para categoria de gasto");
        }
        boolean duplicate = request.month() == null
                ? budgets.existsByUserIdAndCategoryIdAndMonthIsNull(userId, category.getId())
                : budgets.existsByUserIdAndCategoryIdAndMonth(userId, category.getId(), request.month().atDay(1));
        if (duplicate) {
            throw new ConflictException(request.month() == null
                    ? "Essa categoria já tem orçamento recorrente"
                    : "Essa categoria já tem orçamento em " + request.month());
        }
        Budget saved = budgets.save(new Budget(userId, category.getId(), request.month(), request.amount()));
        return statusOf(userId, saved);
    }

    @Transactional
    public BudgetStatus updateAmount(UUID userId, UUID id, BigDecimal amount) {
        Budget budget = find(userId, id);
        budget.setAmount(amount);
        return statusOf(userId, budget);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        budgets.delete(find(userId, id));
    }

    /** Percentual gasto, truncado em 2 casas. */
    static BigDecimal percent(BigDecimal amount, BigDecimal spent) {
        return spent.multiply(HUNDRED).divide(amount, 2, RoundingMode.DOWN);
    }

    /** O nível sai da conta exata, não do percentual arredondado. */
    static BudgetLevel level(BigDecimal amount, BigDecimal spent) {
        if (spent.compareTo(amount) >= 0) {
            return BudgetLevel.ESTOURADO;
        }
        return spent.multiply(HUNDRED).compareTo(amount.multiply(ALERT_PERCENT)) >= 0 ? BudgetLevel.ATENCAO : BudgetLevel.OK;
    }

    /** Situação no mês do orçamento; o recorrente, no mês corrente do usuário. */
    private BudgetStatus statusOf(UUID userId, Budget b) {
        YearMonth month = b.getMonth() != null ? b.getMonth() : YearMonth.now(clock.withZone(userSettings.zoneOf(userId)));
        BigDecimal s = transactions.expenseByCategory(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(t -> t.categoryId().equals(b.getCategoryId())).map(CategoryTotal::total).findFirst()
                .orElse(BigDecimal.ZERO.setScale(2));
        String name = categories.findByIdAndUserId(b.getCategoryId(), userId).map(Category::getName).orElse(null);
        return new BudgetStatus(b.getId(), b.getCategoryId(), name, b.getMonth(), b.getAmount(), s, b.getAmount().subtract(s),
                percent(b.getAmount(), s), level(b.getAmount(), s));
    }

    private Budget find(UUID userId, UUID id) {
        return budgets.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Orçamento não encontrado"));
    }
}
