package com.porganization.commitments.recurrence;

import java.time.DayOfWeek;

/** Dias da semana no formato curto da API e do RRULE ("MON", "WED"...). */
public enum WeekDay {
    MON(DayOfWeek.MONDAY),
    TUE(DayOfWeek.TUESDAY),
    WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY),
    SAT(DayOfWeek.SATURDAY),
    SUN(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    WeekDay(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }

    public static WeekDay of(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}
