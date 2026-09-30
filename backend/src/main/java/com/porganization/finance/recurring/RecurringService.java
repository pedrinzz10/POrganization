package com.porganization.finance.recurring;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.calendar.BusinessCalendar.Adjustment;
import com.porganization.finance.cards.CreditCardService;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryKind;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.recurring.RecurringDtos.RecurringRequest;
import com.porganization.finance.recurring.RecurringDtos.RecurringResponse;
import com.porganization.finance.transactions.TransactionType;
import com.porganization.settings.UserSettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro dos modelos fixos. Editar só afeta os meses que ainda não foram gerados. */
@Service
public class RecurringService {

    private final RecurringTransactionRepository recurring;
    private final AccountRepository accounts;
    private final CreditCardService cards;
    private final CategoryRepository categories;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public RecurringService(RecurringTransactionRepository recurring, AccountRepository accounts, CreditCardService cards,
            CategoryRepository categories, UserSettingsService userSettings, Clock clock) {
        this.recurring = recurring;
        this.accounts = accounts;
        this.cards = cards;
        this.categories = categories;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(UUID userId) {
        LocalDate today = today(userId);
        return recurring.findByUserIdOrderByDayOfMonthAscDescriptionAsc(userId).stream().map(r -> toResponse(r, today)).toList();
    }

    @Transactional
    public RecurringResponse create(UUID userId, RecurringRequest request) {
        RecurringTransaction r = new RecurringTransaction(userId);
        apply(userId, r, request);
        return toResponse(recurring.save(r), today(userId));
    }

    @Transactional
    public RecurringResponse update(UUID userId, UUID id, RecurringRequest request) {
        RecurringTransaction r = find(userId, id);
        apply(userId, r, request);
        return toResponse(r, today(userId));
    }

    /** Exclui o modelo; os lançamentos já gerados ficam (perdem só o vínculo). */
    @Transactional
    public void delete(UUID userId, UUID id) {
        recurring.delete(find(userId, id));
    }

    private RecurringTransaction find(UUID userId, UUID id) {
        return recurring.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Recorrente não encontrado"));
    }

    private void apply(UUID userId, RecurringTransaction r, RecurringRequest request) {
        if (request.type() == TransactionType.TRANSFER) {
            throw new InvalidRequestException("type", "recorrente é renda ou gasto");
        }
        if ((request.accountId() == null) == (request.cardId() == null)) {
            throw new InvalidRequestException("accountId", "informe uma conta ou um cartão");
        }
        if (request.cardId() != null) {
            if (request.type() != TransactionType.EXPENSE) {
                throw new InvalidRequestException("cardId", "no cartão só entra gasto");
            }
            cards.find(userId, request.cardId());
        } else {
            accounts.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        }
        Category category = categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        CategoryKind expected = request.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (category.getKind() != expected) {
            throw new InvalidRequestException("categoryId",
                    expected == CategoryKind.EXPENSE ? "gasto precisa de uma categoria de gasto" : "renda precisa de uma categoria de renda");
        }
        validateRule(request.rule(), request.dayOfMonth(), request.businessDay());
        if (request.endMonth() != null && request.endMonth().isBefore(request.startMonth())) {
            throw new InvalidRequestException("endMonth", "o fim não pode ser antes do início");
        }
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        r.update(request.type(), request.amount(), description, request.accountId(), request.cardId(), category.getId(),
                request.rule(), request.dayOfMonth(), request.businessDay(), request.adjustment(),
                request.startMonth(), request.endMonth());
    }

    /** As próximas {@code count} datas de uma regra a partir de hoje (prévia do formulário). */
    public List<LocalDate> preview(UUID userId, ScheduleRule rule, Integer dayOfMonth, Integer businessDay, Adjustment adjustment,
            int count) {
        validateRule(rule, dayOfMonth, businessDay);
        LocalDate today = today(userId);
        RecurringTransaction draft = new RecurringTransaction(userId);
        draft.update(TransactionType.INCOME, BigDecimal.ONE, null, null, null, null, rule, dayOfMonth, businessDay, adjustment,
                YearMonth.from(today).minusMonths(1), null);
        return nextDates(draft, today, count);
    }

    private static void validateRule(ScheduleRule rule, Integer dayOfMonth, Integer businessDay) {
        if (rule == ScheduleRule.DAY_OF_MONTH && dayOfMonth == null) {
            throw new InvalidRequestException("dayOfMonth", "informe o dia do mês");
        }
        if (rule == ScheduleRule.BUSINESS_DAY && businessDay == null) {
            throw new InvalidRequestException("businessDay", "informe qual dia útil (1 a 15)");
        }
    }

    /** Datas da regra a partir de "from", dentro do período do agendado (olha até 2 anos à frente). */
    private static List<LocalDate> nextDates(RecurringTransaction r, LocalDate from, int count) {
        List<LocalDate> dates = new ArrayList<>();
        // Começa no mês anterior: ANTICIPATE pode puxar a data do mês seguinte para este
        YearMonth month = YearMonth.from(from).minusMonths(1);
        for (int i = 0; i < 26 && dates.size() < count; i++, month = month.plusMonths(1)) {
            if (!r.activeIn(month)) {
                continue;
            }
            LocalDate date = r.dateIn(month);
            if (!date.isBefore(from)) {
                dates.add(date);
            }
        }
        return dates;
    }

    private static RecurringResponse toResponse(RecurringTransaction r, LocalDate today) {
        List<LocalDate> next = nextDates(r, today, 1);
        return new RecurringResponse(r.getId(), r.getType(), r.getAmount(), r.getDescription(), r.getAccountId(), r.getCardId(),
                r.getCategoryId(), r.getRuleType(), r.getDayOfMonth(), r.getBusinessDay(), r.getAdjustment(), r.getStartMonth(),
                r.getEndMonth(), next.isEmpty() ? null : next.getFirst());
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(clock.instant(), userSettings.zoneOf(userId));
    }
}
