package com.porganization.finance.recurring;

import com.porganization.finance.cards.CardStatement;
import com.porganization.finance.cards.CreditCard;
import com.porganization.finance.cards.CreditCardService;
import com.porganization.finance.cards.StatementResolver;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera as transações do mês a partir dos modelos fixos (paid = false). Idempotente: cada
 * (modelo, mês) é marcado em recurring_generations, então gerar de novo não duplica e o que o
 * usuário excluiu não volta. Roda quando o mês é consultado e no job diário (RecurringJob).
 */
@Service
public class RecurringGenerator {

    private final RecurringTransactionRepository recurring;
    private final TransactionRepository transactions;
    private final CreditCardService cards;

    public RecurringGenerator(RecurringTransactionRepository recurring, TransactionRepository transactions,
            CreditCardService cards) {
        this.recurring = recurring;
        this.transactions = transactions;
        this.cards = cards;
    }

    /** Gera o mês para o usuário; devolve quantas transações criou agora. */
    @Transactional
    public int generate(UUID userId, YearMonth month) {
        int created = 0;
        for (RecurringTransaction r : recurring.activeIn(userId, month.atDay(1))) {
            if (recurring.markGenerated(r.getId(), month.atDay(1)) == 1) {
                transactions.save(transactionFor(r, month));
                created++;
            }
        }
        return created;
    }

    /** Uma ocorrência em aberto e o mês a que ela pertence (pela regra que a gerou). */
    public record OpenOccurrence(Transaction transaction, YearMonth month) {
    }

    /**
     * As ocorrências em aberto do modelo, com o mês de cada uma. Chame antes de mudar a regra: com
     * "antecipa", a de outubro pode cair em 30/09, então o mês sai da regra antiga, não da data.
     */
    public List<OpenOccurrence> openOccurrences(RecurringTransaction r) {
        return transactions.findByRecurringIdAndPaidFalse(r.getId()).stream()
                .map(t -> new OpenOccurrence(t, monthOf(r, t.getScheduledDate() != null ? t.getScheduledDate() : t.getDate())))
                .toList();
    }

    /**
     * Depois de editar o modelo, leva a mudança para as ocorrências em aberto: valor, descrição,
     * categoria, conta/cartão e a data pela regra nova (a remarcada à mão mantém a data escolhida).
     * A que ficou fora do período sai, e o mês pode ser gerado de novo se o período voltar a incluí-lo.
     * As pagas não mudam: são o que de fato aconteceu.
     */
    @Transactional
    public void resync(RecurringTransaction r, List<OpenOccurrence> open) {
        for (OpenOccurrence o : open) {
            Transaction t = o.transaction();
            if (!r.activeIn(o.month())) {
                transactions.delete(t);
                recurring.unmarkGenerated(r.getId(), o.month().atDay(1));
                continue;
            }
            boolean rescheduled = t.getScheduledDate() != null && !t.getScheduledDate().equals(t.getDate());
            LocalDate date = r.dateIn(o.month());
            t.setScheduledDate(date);
            if (!rescheduled) {
                t.setDate(date);
            }
            t.setType(r.getType());
            t.setAmount(r.getAmount());
            t.setDescription(r.getDescription());
            t.setCategoryId(r.getCategoryId());
            if (r.getCardId() != null) {
                t.setAccountId(null);
                t.setCardStatementId(openStatementFor(cards.find(r.getUserId(), r.getCardId()), t.getDate()).getId());
            } else {
                t.setCardStatementId(null);
                t.setAccountId(r.getAccountId());
            }
        }
    }

    /** Ao excluir o modelo, as ocorrências em aberto vão junto; as pagas ficam no extrato. */
    @Transactional
    public void deleteOpen(RecurringTransaction r) {
        transactions.findByRecurringIdAndPaidFalse(r.getId()).forEach(transactions::delete);
    }

    /** O mês cuja data pela regra é essa: o da própria data ou o seguinte (antecipada). */
    private static YearMonth monthOf(RecurringTransaction r, LocalDate date) {
        YearMonth same = YearMonth.from(date);
        if (r.dateIn(same).equals(date)) {
            return same;
        }
        return r.dateIn(same.plusMonths(1)).equals(date) ? same.plusMonths(1) : same;
    }

    /** O dia N no mês; 31 em mês curto cai no último dia (a regra completa está em RecurringTransaction.dateIn). */
    public static LocalDate dateFor(int dayOfMonth, YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private Transaction transactionFor(RecurringTransaction r, YearMonth month) {
        LocalDate date = r.dateIn(month);
        Transaction t = new Transaction(r.getUserId(), r.getType(), r.getAmount(), date);
        t.setScheduledDate(date);
        t.setCategoryId(r.getCategoryId());
        t.setDescription(r.getDescription());
        t.setPaid(false);
        t.setRecurringId(r.getId());
        if (r.getCardId() != null) {
            t.setCardStatementId(openStatementFor(cards.find(r.getUserId(), r.getCardId()), t.getDate()).getId());
        } else {
            t.setAccountId(r.getAccountId());
        }
        return t;
    }

    /** A fatura da data (StatementResolver); se já foi paga, a próxima que ainda não foi. */
    private CardStatement openStatementFor(CreditCard card, LocalDate date) {
        CardStatement statement = cards.statementFor(card, date);
        while (statement.getStatus() == CardStatement.StoredStatus.PAID) {
            YearMonth nextClosing = YearMonth.from(statement.getClosingDate()).plusMonths(1);
            statement = cards.statementForPeriod(card,
                    StatementResolver.forClosingMonth(card.getClosingDay(), card.getDueDay(), nextClosing));
        }
        return statement;
    }
}
