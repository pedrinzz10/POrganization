package com.porganization.finance.recurring;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.Account;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.recurring.ScheduledDtos.ConfirmRequest;
import com.porganization.finance.recurring.ScheduledDtos.Occurrence;
import com.porganization.finance.recurring.ScheduledDtos.RescheduleResponse;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.settings.UserSettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As ocorrências dos agendados em conta e o que o usuário faz com cada uma (F19): confirmar
 * ("recebi"/"paguei", com valor e data ajustáveis), remarcar ou cancelar só aquela ocorrência.
 * A regra do agendado nunca muda por causa disso.
 */
@Service
public class ScheduledService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RecurringGenerator generator;
    private final RecurringTransactionRepository recurring;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final UserSettingsService userSettings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ScheduledService(RecurringGenerator generator, RecurringTransactionRepository recurring,
            TransactionRepository transactions, AccountRepository accounts, UserSettingsService userSettings, JdbcTemplate jdbc,
            Clock clock) {
        this.generator = generator;
        this.recurring = recurring;
        this.transactions = transactions;
        this.accounts = accounts;
        this.userSettings = userSettings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Ocorrências do mês (pela data da regra), das pendentes às já tratadas. Gera o mês antes. */
    public List<Occurrence> month(UUID userId, YearMonth month) {
        generator.generate(userId, month);
        LocalDate today = today(userId);
        Map<UUID, RecurringTransaction> rules = rules(userId);
        Map<UUID, String> accountNames = accountNames(userId);

        List<Occurrence> result = new ArrayList<>();
        for (Transaction t : transactions.findByUserIdAndRecurringIdIsNotNullAndAccountIdIsNotNullAndScheduledDateBetween(
                userId, month.atDay(1), month.atEndOfMonth())) {
            result.add(toOccurrence(t, rules.get(t.getRecurringId()), accountNames, today));
        }
        // Canceladas: o lançamento saiu, a marca do mês ficou
        jdbc.query("""
                select g.recurring_id from recurring_generations g
                join recurring_transactions r on r.id = g.recurring_id
                where r.user_id = ? and g.month = ? and g.skipped and r.account_id is not null
                """, rs -> {
            RecurringTransaction r = rules.get(rs.getObject(1, UUID.class));
            if (r != null) {
                LocalDate date = r.dateIn(month);
                result.add(new Occurrence(null, r.getId(), r.getType(), r.getDescription(), r.getAmount(), r.getAmount(),
                        r.getAccountId(), accountNames.get(r.getAccountId()), date, date, OccurrenceStatus.CANCELLED));
            }
        }, userId, month.atDay(1));
        result.sort(Comparator.comparing(Occurrence::date).thenComparing(o -> o.description() == null ? "" : o.description()));
        return result;
    }

    @Transactional
    public Occurrence confirm(UUID userId, UUID id, ConfirmRequest request) {
        Transaction t = pending(userId, id);
        LocalDate today = today(userId);
        LocalDate date = request.date() != null ? request.date() : today;
        if (date.isAfter(today)) {
            throw new InvalidRequestException("date", "a data em que caiu não pode ser no futuro");
        }
        if (request.amount() != null) {
            t.setAmount(request.amount());
        }
        t.setDate(date);
        t.setPaid(true);
        return toOccurrence(t, rules(userId).get(t.getRecurringId()), accountNames(userId), today);
    }

    @Transactional
    public RescheduleResponse reschedule(UUID userId, UUID id, LocalDate newDate) {
        Transaction t = pending(userId, id);
        LocalDate today = today(userId);
        if (!newDate.isAfter(today)) {
            throw new InvalidRequestException("date", "a nova data tem que ser a partir de amanhã");
        }
        t.setDate(newDate);
        RecurringTransaction rule = rules(userId).get(t.getRecurringId());
        String warning = null;
        if (rule != null) {
            YearMonth next = YearMonth.from(scheduledDate(t)).plusMonths(1);
            LocalDate nextOccurrence = rule.activeIn(next) ? rule.dateIn(next) : null;
            if (nextOccurrence != null && !newDate.isBefore(nextOccurrence)) {
                warning = "A nova data passa da próxima ocorrência (%s).".formatted(nextOccurrence.format(DAY));
            }
        }
        return new RescheduleResponse(toOccurrence(t, rule, accountNames(userId), today), warning);
    }

    /** "Não vou receber/pagar este mês": o lançamento sai (de saldo, extrato e totais) e o mês fica marcado. */
    @Transactional
    public void skip(UUID userId, UUID id) {
        Transaction t = pending(userId, id);
        jdbc.update("""
                update recurring_generations set skipped = true, skipped_transaction_id = ?
                where recurring_id = ? and month = ?
                """, t.getId(), t.getRecurringId(), YearMonth.from(scheduledDate(t)).atDay(1));
        transactions.delete(t);
    }

    /** A ocorrência ainda em aberto: já confirmada ou cancelada → 409; não é ocorrência de agendado → 404. */
    private Transaction pending(UUID userId, UUID id) {
        Optional<Transaction> found = transactions.findByIdAndUserId(id, userId)
                .filter(t -> t.getRecurringId() != null && t.getAccountId() != null);
        if (found.isEmpty()) {
            Integer skipped = jdbc.queryForObject("""
                    select count(*) from recurring_generations g join recurring_transactions r on r.id = g.recurring_id
                    where g.skipped_transaction_id = ? and r.user_id = ?
                    """, Integer.class, id, userId);
            if (skipped != null && skipped > 0) {
                throw new ConflictException("Essa ocorrência foi cancelada");
            }
            throw new NotFoundException("Ocorrência não encontrada");
        }
        if (found.get().isPaid()) {
            throw new ConflictException("Essa ocorrência já foi confirmada");
        }
        return found.get();
    }

    private static LocalDate scheduledDate(Transaction t) {
        return t.getScheduledDate() != null ? t.getScheduledDate() : t.getDate();
    }

    private static Occurrence toOccurrence(Transaction t, RecurringTransaction rule, Map<UUID, String> accountNames, LocalDate today) {
        LocalDate scheduled = scheduledDate(t);
        return new Occurrence(t.getId(), t.getRecurringId(), t.getType(), t.getDescription(), t.getAmount(),
                rule != null ? rule.getAmount() : t.getAmount(), t.getAccountId(), accountNames.get(t.getAccountId()), scheduled,
                t.getDate(), OccurrenceStatus.of(t.isPaid(), t.getDate(), scheduled, today));
    }

    private Map<UUID, RecurringTransaction> rules(UUID userId) {
        return recurring.findByUserIdOrderByDayOfMonthAscDescriptionAsc(userId).stream()
                .collect(Collectors.toMap(RecurringTransaction::getId, Function.identity()));
    }

    private Map<UUID, String> accountNames(UUID userId) {
        return accounts.findByUserIdOrderByNameAsc(userId).stream().collect(Collectors.toMap(Account::getId, Account::getName));
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(clock.instant(), userSettings.zoneOf(userId));
    }
}
