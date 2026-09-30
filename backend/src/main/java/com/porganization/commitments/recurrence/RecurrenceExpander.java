package com.porganization.commitments.recurrence;

import com.porganization.common.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera as datas de ocorrência de uma série dentro de [from, to]. Função pura, sem banco.
 *
 * <p>A série é percorrida por períodos (dia, semana, mês ou ano). Sem count, pula direto
 * para o período que contém "from", então uma série infinita que começou há anos não é
 * percorrida desde o início. Com count é preciso contar desde o começo, mas count é limitado.
 */
public final class RecurrenceExpander {

    public static final int MAX_COUNT = 1000;
    public static final int MAX_INTERVAL = 99;

    private RecurrenceExpander() {
    }

    public static List<LocalDate> expand(LocalDate start, RecurrenceRule rule, LocalDate from, LocalDate to) {
        List<LocalDate> result = new ArrayList<>();
        if (to.isBefore(from) || to.isBefore(start)) {
            return result;
        }
        int interval = interval(rule);
        long period = rule.count() == null ? firstPeriodNear(start, rule.freq(), interval, from) : 0;
        int emitted = 0;

        while (true) {
            List<LocalDate> candidates = datesOfPeriod(start, rule, interval, period);
            if (candidates.isEmpty() && periodStart(start, rule.freq(), interval, period).isAfter(to)) {
                return result;
            }
            for (LocalDate date : candidates) {
                if (date.isBefore(start)) {
                    continue;
                }
                if (date.isAfter(to) || (rule.until() != null && date.isAfter(rule.until()))) {
                    return result;
                }
                emitted++;
                if (rule.count() != null && emitted > rule.count()) {
                    return result;
                }
                if (!date.isBefore(from)) {
                    result.add(date);
                }
            }
            period++;
        }
    }

    /** Lança InvalidRequestException com o campo "recurrenceRule.x" se a regra não fizer sentido. */
    public static void validate(LocalDate start, RecurrenceRule rule) {
        if (rule.freq() == null) {
            throw new InvalidRequestException("recurrenceRule.freq", "informe a frequência");
        }
        if (rule.interval() != null && (rule.interval() < 1 || rule.interval() > MAX_INTERVAL)) {
            throw new InvalidRequestException("recurrenceRule.interval", "deve estar entre 1 e " + MAX_INTERVAL);
        }
        if (rule.until() != null && rule.count() != null) {
            throw new InvalidRequestException("recurrenceRule.count", "use data final (until) ou quantidade (count), não os dois");
        }
        if (rule.count() != null && (rule.count() < 1 || rule.count() > MAX_COUNT)) {
            throw new InvalidRequestException("recurrenceRule.count", "deve estar entre 1 e " + MAX_COUNT);
        }
        if (rule.until() != null && rule.until().isBefore(start)) {
            throw new InvalidRequestException("recurrenceRule.until", "deve ser igual ou depois da data do compromisso");
        }
        if (rule.byWeekDays() != null && !rule.byWeekDays().isEmpty() && rule.freq() != Frequency.WEEKLY) {
            throw new InvalidRequestException("recurrenceRule.byWeekDays", "só vale para frequência semanal");
        }
    }

    private static int interval(RecurrenceRule rule) {
        return rule.interval() == null ? 1 : rule.interval();
    }

    /** Todas as datas geradas pelo período de índice "period" (antes de filtrar por start/until/count). */
    private static List<LocalDate> datesOfPeriod(LocalDate start, RecurrenceRule rule, int interval, long period) {
        long step = period * interval;
        return switch (rule.freq()) {
            case DAILY -> List.of(start.plusDays(step));
            case MONTHLY -> List.of(clampedDay(YearMonth.from(start).plusMonths(step), start.getDayOfMonth()));
            // plusYears já transforma 29/02 em 28/02 nos anos comuns
            case YEARLY -> List.of(start.plusYears(step));
            case WEEKLY -> {
                LocalDate monday = weekAnchor(start).plusWeeks(step);
                yield weekDays(start, rule).stream().map(day -> monday.plusDays(day.getValue() - 1L)).toList();
            }
        };
    }

    private static LocalDate periodStart(LocalDate start, Frequency freq, int interval, long period) {
        long step = period * interval;
        return switch (freq) {
            case DAILY -> start.plusDays(step);
            case WEEKLY -> weekAnchor(start).plusWeeks(step);
            case MONTHLY -> YearMonth.from(start).plusMonths(step).atDay(1);
            case YEARLY -> start.withDayOfYear(1).plusYears(step);
        };
    }

    /** Índice do período que contém "from" (ou 0, se from vem antes do início). */
    private static long firstPeriodNear(LocalDate start, Frequency freq, int interval, LocalDate from) {
        if (!from.isAfter(start)) {
            return 0;
        }
        long units = switch (freq) {
            case DAILY -> ChronoUnit.DAYS.between(start, from);
            case WEEKLY -> ChronoUnit.WEEKS.between(weekAnchor(start), weekAnchor(from));
            case MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(from));
            case YEARLY -> from.getYear() - start.getYear();
        };
        return Math.max(0, units / interval);
    }

    private static List<DayOfWeek> weekDays(LocalDate start, RecurrenceRule rule) {
        if (rule.byWeekDays() == null || rule.byWeekDays().isEmpty()) {
            return List.of(start.getDayOfWeek());
        }
        return rule.byWeekDays().stream().map(WeekDay::toDayOfWeek).distinct().sorted().toList();
    }

    private static LocalDate weekAnchor(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate clampedDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
