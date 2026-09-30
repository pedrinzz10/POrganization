package com.porganization.finance.cards;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Decide em que fatura uma compra no cartão cai. Função pura.
 * <ul>
 * <li>Fechamento do mês: o dia de fechamento, limitado ao último dia do mês (31 em fevereiro = 28/29).</li>
 * <li>Compra antes do fechamento entra na fatura que fecha naquele mês; no dia do fechamento ou
 * depois, na que fecha no mês seguinte.</li>
 * <li>Vencimento: se o dia de vencimento é depois do de fechamento, vence no mesmo mês do fechamento;
 * senão, no mês seguinte.</li>
 * <li>A fatura é identificada pelo mês do vencimento ("fatura de outubro").</li>
 * </ul>
 */
public final class StatementResolver {

    private StatementResolver() {
    }

    public record StatementPeriod(YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate) {
    }

    public static StatementPeriod resolve(int closingDay, int dueDay, LocalDate purchaseDate) {
        YearMonth closingMonth = YearMonth.from(purchaseDate);
        if (!purchaseDate.isBefore(closingDate(closingDay, closingMonth))) {
            closingMonth = closingMonth.plusMonths(1);
        }
        return forClosingMonth(closingDay, dueDay, closingMonth);
    }

    /** A fatura que fecha no mês informado. */
    public static StatementPeriod forClosingMonth(int closingDay, int dueDay, YearMonth closingMonth) {
        LocalDate closing = closingDate(closingDay, closingMonth);
        YearMonth dueMonth = dueDay > closingDay ? closingMonth : closingMonth.plusMonths(1);
        LocalDate due = dayOf(dueMonth, dueDay);
        return new StatementPeriod(YearMonth.from(due), closing, due);
    }

    public static LocalDate closingDate(int closingDay, YearMonth month) {
        return dayOf(month, closingDay);
    }

    private static LocalDate dayOf(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
