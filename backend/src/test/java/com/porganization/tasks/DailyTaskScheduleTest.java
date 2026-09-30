package com.porganization.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.commitments.recurrence.WeekDay;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyTaskScheduleTest {

    private static final EnumSet<WeekDay> SEG_QUA_SEX = EnumSet.of(WeekDay.MON, WeekDay.WED, WeekDay.FRI);

    // T01 T4 (CA4)
    @Test
    void mudancaDeDiasValeDaDataDelaEmDiante() {
        TaskSchedule agenda = new TaskSchedule(LocalDate.parse("2026-10-01"), List.of(
                new TaskSchedule.Rule(LocalDate.parse("2026-10-08"), TaskSchedule.EVERY_DAY),
                new TaskSchedule.Rule(LocalDate.parse("2026-10-01"), SEG_QUA_SEX)));

        assertThat(agenda.isDue(LocalDate.parse("2026-10-07"))).isTrue();  // quarta, regra antiga
        assertThat(agenda.isDue(LocalDate.parse("2026-10-06"))).isFalse(); // terça, regra antiga
        assertThat(agenda.isDue(LocalDate.parse("2026-10-14"))).isTrue();  // terça, regra nova
        assertThat(agenda.weekdaysOn(LocalDate.parse("2026-10-07"))).isEqualTo(SEG_QUA_SEX);
    }

    @Test
    void antesDaCriacaoNuncaContaESemRegraValeTodoDia() {
        TaskSchedule agenda = new TaskSchedule(LocalDate.parse("2026-10-07"), List.of());

        assertThat(agenda.isDue(LocalDate.parse("2026-10-06"))).isFalse();
        assertThat(agenda.isDue(LocalDate.parse("2026-10-07"))).isTrue();
        assertThat(agenda.isDue(LocalDate.parse("2026-10-10"))).isTrue();
    }
}
