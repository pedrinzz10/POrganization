package com.porganization.finance.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Dias úteis bancários: segunda a sexta, menos os feriados de {@link Holidays}. Função pura, usada
 * pelos agendados (F18) para achar "o 5º dia útil do mês" ou mover uma data que caiu no fim de semana.
 */
public final class BusinessCalendar {

    /** O que fazer quando a data cai num dia não útil. */
    public enum Adjustment {
        /** Fica na data mesmo assim. */
        KEEP,
        /** Vai para o dia útil anterior. */
        ANTICIPATE,
        /** Vai para o dia útil seguinte. */
        POSTPONE
    }

    private BusinessCalendar() {
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !Holidays.isHoliday(date);
    }

    /** O n-ésimo dia útil do mês (1 = primeiro). n maior que os dias úteis do mês é erro. */
    public static LocalDate nthBusinessDay(YearMonth month, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n deve ser pelo menos 1");
        }
        int count = 0;
        for (LocalDate day = month.atDay(1); !day.isAfter(month.atEndOfMonth()); day = day.plusDays(1)) {
            if (isBusinessDay(day) && ++count == n) {
                return day;
            }
        }
        throw new IllegalArgumentException("%s tem só %d dias úteis, não %d".formatted(month, count, n));
    }

    public static LocalDate lastBusinessDay(YearMonth month) {
        LocalDate day = month.atEndOfMonth();
        while (!isBusinessDay(day)) {
            day = day.minusDays(1);
        }
        return day;
    }

    /** Data útil nunca muda; não útil vai para o dia útil anterior ou seguinte, ou fica (KEEP). */
    public static LocalDate adjust(LocalDate date, Adjustment adjustment) {
        LocalDate day = date;
        switch (adjustment) {
            case KEEP -> {
                return date;
            }
            case ANTICIPATE -> {
                while (!isBusinessDay(day)) {
                    day = day.minusDays(1);
                }
            }
            case POSTPONE -> {
                while (!isBusinessDay(day)) {
                    day = day.plusDays(1);
                }
            }
        }
        return day;
    }
}
