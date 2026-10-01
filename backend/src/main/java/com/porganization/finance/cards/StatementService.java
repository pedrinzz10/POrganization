package com.porganization.finance.cards;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.cards.CardDtos.StatementItem;
import com.porganization.finance.cards.CardDtos.StatementResponse;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.finance.transactions.TransactionType;
import com.porganization.settings.UserSettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e pagamento de fatura. O pagamento é um gasto na conta de pagamento do cartão ligado à
 * fatura (conta e fatura preenchidas); ele tira o dinheiro da conta, mas não entra nos totais do mês,
 * porque as compras já contaram no mês delas.
 */
@Service
public class StatementService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final CreditCardService cards;
    private final CardStatementRepository statements;
    private final TransactionRepository transactions;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public StatementService(CreditCardService cards, CardStatementRepository statements, TransactionRepository transactions,
            UserSettingsService userSettings, Clock clock) {
        this.cards = cards;
        this.statements = statements;
        this.transactions = transactions;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    /** A fatura que vence no mês informado; sem compras ainda, volta vazia e sem id. */
    @Transactional(readOnly = true)
    public StatementResponse ofMonth(UUID userId, UUID cardId, YearMonth month) {
        CreditCard card = cards.find(userId, cardId);
        return statements.findByUserIdAndCardIdAndReferenceMonth(userId, cardId, month.atDay(1))
                .map(s -> toResponse(userId, card, s))
                .orElseGet(() -> {
                    // A fatura "de outubro" fecha em outubro se vence depois do fechamento, senão em setembro
                    YearMonth closingMonth = card.getDueDay() > card.getClosingDay() ? month : month.minusMonths(1);
                    StatementResolver.StatementPeriod period =
                            StatementResolver.forClosingMonth(card.getClosingDay(), card.getDueDay(), closingMonth);
                    String status = today(userId).isAfter(period.closingDate()) ? "CLOSED" : "OPEN";
                    return new StatementResponse(null, cardId, period.referenceMonth(), period.closingDate(), period.dueDate(),
                            status, BigDecimal.ZERO.setScale(2), card.getCreditLimit(), availableLimit(userId, card), null,
                            null, List.of());
                });
    }

    /** Paga a fatura inteira com a conta de pagamento do cartão. Fatura já paga → 409. */
    @Transactional
    public StatementResponse pay(UUID userId, UUID statementId) {
        CardStatement statement = statements.findByIdAndUserId(statementId, userId)
                .orElseThrow(() -> new NotFoundException("Fatura não encontrada"));
        if (statement.getStatus() == CardStatement.StoredStatus.PAID) {
            throw new ConflictException("Essa fatura já foi paga");
        }
        BigDecimal total = transactions.statementTotal(userId, statementId);
        if (total.signum() == 0) {
            throw new InvalidRequestException("statementId", "a fatura não tem compras para pagar");
        }
        CreditCard card = cards.find(userId, statement.getCardId());

        Transaction payment = new Transaction(userId, TransactionType.EXPENSE, total, today(userId));
        payment.setAccountId(card.getPaymentAccountId());
        payment.setCardStatementId(statement.getId());
        payment.setDescription("Fatura %s %s".formatted(card.getName(), statement.getReferenceMonth().format(MONTH)));
        payment.setPaid(true);
        transactions.save(payment);
        statement.markPaid(clock.instant());
        return toResponse(userId, card, statement);
    }

    /** Limite − compras de todas as faturas não pagas (inclusive parcelas futuras). */
    public BigDecimal availableLimit(UUID userId, CreditCard card) {
        return card.getCreditLimit().subtract(statements.unpaidTotal(userId, card.getId()));
    }

    private StatementResponse toResponse(UUID userId, CreditCard card, CardStatement s) {
        List<StatementItem> items = transactions
                .findByUserIdAndCardStatementIdAndAccountIdIsNullOrderByDateAscCreatedAtAsc(userId, s.getId()).stream()
                .map(t -> new StatementItem(t.getId(), t.getDate(), t.getDescription(), t.getAmount(), t.getCategoryId(),
                        t.getPurchaseId(), t.getInstallmentNumber(), t.getInstallmentCount(), t.getRecurringId()))
                .toList();
        BigDecimal total = items.stream().map(StatementItem::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        UUID paymentId = transactions.findFirstByUserIdAndCardStatementIdAndAccountIdIsNotNull(userId, s.getId())
                .map(Transaction::getId).orElse(null);
        return new StatementResponse(s.getId(), card.getId(), s.getReferenceMonth(), s.getClosingDate(), s.getDueDate(),
                s.statusOn(today(userId)), total, card.getCreditLimit(), availableLimit(userId, card), s.getPaidAt(),
                paymentId, items);
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(clock.instant(), userSettings.zoneOf(userId));
    }
}
