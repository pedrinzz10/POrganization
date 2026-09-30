package com.porganization.tasks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/**
 * Sequência e conclusão de uma tarefa (T02). Função pura: só contam os dias devidos (regra da época,
 * a partir da criação), e hoje só entra se já foi feito, porque o dia ainda não acabou.
 *
 * @param streak         dias devidos seguidos com a tarefa feita, terminando hoje (se feita) ou ontem
 * @param completionRate % dos dias devidos feitos nos últimos 30 dias (2 casas, truncado); null sem dia devido
 */
public record TaskStats(int streak, BigDecimal completionRate) {

    static final int WINDOW_DAYS = 30;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static TaskStats of(TaskSchedule schedule, Set<LocalDate> done, LocalDate today) {
        return new TaskStats(streak(schedule, done, today), rate(schedule, done, today));
    }

    static int streak(TaskSchedule schedule, Set<LocalDate> done, LocalDate today) {
        int streak = 0;
        for (LocalDate day = today; !day.isBefore(schedule.createdOn()); day = day.minusDays(1)) {
            if (!schedule.isDue(day)) {
                continue;
            }
            if (done.contains(day)) {
                streak++;
            } else if (!day.equals(today)) {
                // Hoje pendente não quebra; um dia devido anterior sem marcação quebra
                break;
            }
        }
        return streak;
    }

    static BigDecimal rate(TaskSchedule schedule, Set<LocalDate> done, LocalDate today) {
        int due = 0;
        int completed = 0;
        for (LocalDate day = today.minusDays(WINDOW_DAYS - 1); !day.isAfter(today); day = day.plusDays(1)) {
            if (!schedule.isDue(day) || (day.equals(today) && !done.contains(day))) {
                continue;
            }
            due++;
            if (done.contains(day)) {
                completed++;
            }
        }
        return due == 0 ? null : BigDecimal.valueOf(completed).multiply(HUNDRED).divide(BigDecimal.valueOf(due), 2, RoundingMode.DOWN);
    }
}
