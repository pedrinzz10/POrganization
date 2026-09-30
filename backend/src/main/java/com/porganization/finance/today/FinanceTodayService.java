package com.porganization.finance.today;

import com.porganization.finance.budgets.BudgetDtos.BudgetLevel;
import com.porganization.finance.budgets.BudgetService;
import com.porganization.finance.cards.CardStatement;
import com.porganization.finance.cards.CardStatementRepository;
import com.porganization.finance.cards.CreditCard;
import com.porganization.finance.cards.CreditCardRepository;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.today.FinanceToday.DueItem;
import com.porganization.finance.today.FinanceToday.DueKind;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.finance.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Monta a seção de finanças da tela Hoje: o que vence em breve, orçamentos em alerta e o gasto do dia. */
@Service
public class FinanceTodayService {

    /** Hoje e os próximos 3 dias. */
    public static final int DUE_WINDOW_DAYS = 3;

    private final CardStatementRepository statements;
    private final CreditCardRepository cards;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final BudgetService budgets;

    public FinanceTodayService(CardStatementRepository statements, CreditCardRepository cards,
            TransactionRepository transactions, CategoryRepository categories, BudgetService budgets) {
        this.statements = statements;
        this.cards = cards;
        this.transactions = transactions;
        this.categories = categories;
        this.budgets = budgets;
    }

    @Transactional(readOnly = true)
    public FinanceToday of(UUID userId, LocalDate today) {
        return new FinanceToday(
                dueSoon(userId, today),
                budgets.status(userId, YearMonth.from(today)).stream().filter(b -> b.level() != BudgetLevel.OK).toList(),
                transactions.totalOf(userId, TransactionType.EXPENSE, today, today));
    }

    private List<DueItem> dueSoon(UUID userId, LocalDate today) {
        LocalDate until = today.plusDays(DUE_WINDOW_DAYS);
        List<DueItem> items = new ArrayList<>();

        Map<UUID, String> cardNames = cards.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(CreditCard::getId, CreditCard::getName));
        for (CardStatement s : statements.findByUserIdAndDueDateBetweenOrderByDueDateAsc(userId, today, until)) {
            if (s.getStatus() == CardStatement.StoredStatus.PAID) {
                continue;
            }
            BigDecimal total = transactions.statementTotal(userId, s.getId());
            if (total.signum() > 0) {
                items.add(new DueItem(DueKind.STATEMENT, s.getId(), "Fatura " + cardNames.getOrDefault(s.getCardId(), "do cartão"),
                        s.getDueDate(), total, s.getCardId(), s.getReferenceMonth()));
            }
        }

        Map<UUID, Category> byId = categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        for (Transaction t : transactions.findByUserIdAndTypeAndPaidFalseAndAccountIdIsNotNullAndDateBetweenOrderByDateAsc(
                userId, TransactionType.EXPENSE, today, until)) {
            String title = t.getDescription() != null ? t.getDescription()
                    : byId.containsKey(t.getCategoryId()) ? byId.get(t.getCategoryId()).getName() : "Conta a pagar";
            items.add(new DueItem(DueKind.BILL, t.getId(), title, t.getDate(), t.getAmount(), null, null));
        }

        items.sort(Comparator.comparing(DueItem::dueDate).thenComparing(DueItem::title));
        return items;
    }
}
