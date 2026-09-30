package com.porganization.finance.calendar;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feriados em que os bancos não abrem, no Brasil: os nacionais de data fixa e os móveis que
 * dependem da Páscoa (Carnaval, Sexta-feira Santa e Corpus Christi). Sem feriados estaduais ou
 * municipais. Calculado localmente, sem serviço externo.
 */
public final class Holidays {

    /** 1/1, Tiradentes, Dia do Trabalho, Independência, Aparecida, Finados, República, Consciência Negra e Natal. */
    static final List<MonthDay> FIXED = List.of(
            MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(5, 1), MonthDay.of(9, 7), MonthDay.of(10, 12),
            MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(11, 20), MonthDay.of(12, 25));

    private static final Map<Integer, Set<LocalDate>> CACHE = new ConcurrentHashMap<>();

    private Holidays() {
    }

    /** Domingo de Páscoa pelo algoritmo de Meeus/Jones/Butcher (calendário gregoriano). */
    public static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    /** Todos os feriados bancários do ano, em ordem. */
    public static Set<LocalDate> of(int year) {
        return CACHE.computeIfAbsent(year, y -> {
            Set<LocalDate> days = new TreeSet<>();
            FIXED.forEach(md -> days.add(md.atYear(y)));
            LocalDate easter = easter(y);
            days.add(easter.minusDays(48)); // segunda de Carnaval
            days.add(easter.minusDays(47)); // terça de Carnaval
            days.add(easter.minusDays(2));  // Sexta-feira Santa
            days.add(easter.plusDays(60));  // Corpus Christi
            return Set.copyOf(days);
        });
    }

    public static boolean isHoliday(LocalDate date) {
        return of(date.getYear()).contains(date);
    }
}
