package com.porganization.commitments.recurrence;

import java.time.LocalDate;
import java.util.List;

/**
 * Como um compromisso se repete. Guardada em commitments.recurrence_rule (jsonb).
 *
 * @param freq       DAILY, WEEKLY, MONTHLY ou YEARLY
 * @param interval   a cada quantas unidades de freq (1 = todo dia/semana/mês/ano)
 * @param byWeekDays só para WEEKLY: em quais dias da semana
 * @param until      último dia possível (inclusive); exclusivo com count
 * @param count      total de ocorrências; exclusivo com until
 */
public record RecurrenceRule(Frequency freq, Integer interval, List<WeekDay> byWeekDays, LocalDate until,
        Integer count) {
}
