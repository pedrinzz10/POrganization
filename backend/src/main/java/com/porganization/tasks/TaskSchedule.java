package com.porganization.tasks;

import com.porganization.commitments.recurrence.WeekDay;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Em que dias uma tarefa é devida. Função pura: a tarefa vale a partir do dia da criação, e cada
 * mudança de dias da semana tem vigência (vale do seu dia em diante), então um dia passado segue a
 * regra que valia nele.
 */
public record TaskSchedule(LocalDate createdOn, List<Rule> rules) {

    /** Dias da semana válidos a partir de validFrom. */
    public record Rule(LocalDate validFrom, Set<WeekDay> weekdays) {
    }

    public static final Set<WeekDay> EVERY_DAY = EnumSet.allOf(WeekDay.class);

    public TaskSchedule {
        rules = rules.stream().sorted(Comparator.comparing(Rule::validFrom)).toList();
    }

    /** A regra que vale no dia (a de vigência mais recente até ele). */
    public Set<WeekDay> weekdaysOn(LocalDate day) {
        Set<WeekDay> current = rules.isEmpty() ? EVERY_DAY : rules.getFirst().weekdays();
        for (Rule rule : rules) {
            if (rule.validFrom().isAfter(day)) {
                break;
            }
            current = rule.weekdays();
        }
        return current;
    }

    /** A tarefa conta nesse dia? Não antes da criação; depois, se o dia da semana está na regra da época. */
    public boolean isDue(LocalDate day) {
        return !day.isBefore(createdOn) && weekdaysOn(day).contains(WeekDay.of(day.getDayOfWeek()));
    }
}
