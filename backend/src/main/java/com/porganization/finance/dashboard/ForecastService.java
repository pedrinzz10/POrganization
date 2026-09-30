package com.porganization.finance.dashboard;

import com.porganization.finance.accounts.AccountDtos.AccountResponse;
import com.porganization.finance.accounts.AccountService;
import com.porganization.finance.dashboard.DashboardResponse.AccountBalance;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.finance.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saldo por conta, total e previsto para o fim do mês (F21): previsto = saldo das contas ativas
 * + o que falta receber − o que falta pagar, contando os agendados em conta ainda não confirmados
 * com data até o fim do mês (previstos, para hoje, atrasados e remarcados para dentro do mês).
 * Confirmados já estão no saldo; cancelados saíram.
 */
@Service
public class ForecastService {

    private final AccountService accounts;
    private final TransactionRepository transactions;

    public ForecastService(AccountService accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public record Forecast(List<AccountBalance> accounts, BigDecimal totalBalance, BigDecimal receivable, BigDecimal payable,
            BigDecimal forecast) {
    }

    @Transactional(readOnly = true)
    public Forecast of(UUID userId, YearMonth month) {
        List<AccountResponse> active = accounts.list(userId, false);
        BigDecimal total = active.stream().map(AccountResponse::balance).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        Set<UUID> activeIds = active.stream().map(AccountResponse::id).collect(Collectors.toSet());

        List<Transaction> open = transactions
                .findByUserIdAndRecurringIdIsNotNullAndAccountIdIsNotNullAndPaidFalseAndDateLessThanEqualOrderByDateAsc(
                        userId, month.atEndOfMonth())
                .stream().filter(t -> activeIds.contains(t.getAccountId())).toList();
        BigDecimal receivable = sum(open, TransactionType.INCOME);
        BigDecimal payable = sum(open, TransactionType.EXPENSE);

        return new Forecast(active.stream().map(a -> new AccountBalance(a.id(), a.name(), a.balance())).toList(), total,
                receivable, payable, total.add(receivable).subtract(payable));
    }

    private static BigDecimal sum(List<Transaction> open, TransactionType type) {
        return open.stream().filter(t -> t.getType() == type).map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
