package com.porganization.finance.recurring;

import com.porganization.finance.cards.CardStatement;
import com.porganization.finance.cards.CreditCard;
import com.porganization.finance.cards.CreditCardService;
import com.porganization.finance.cards.StatementResolver;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
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

    /** O dia do modelo no mês; 31 em mês curto cai no último dia. */
    public static LocalDate dateFor(int dayOfMonth, YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private Transaction transactionFor(RecurringTransaction r, YearMonth month) {
        Transaction t = new Transaction(r.getUserId(), r.getType(), r.getAmount(), dateFor(r.getDayOfMonth(), month));
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
