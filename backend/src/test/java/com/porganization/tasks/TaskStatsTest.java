package com.porganization.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.commitments.recurrence.WeekDay;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskStatsTest {

    private static final LocalDate CRIACAO = LocalDate.parse("2026-10-01");
    private static final LocalDate HOJE = LocalDate.parse("2026-10-08"); // quinta

    private static TaskSchedule agenda(Set<WeekDay> dias) {
        return new TaskSchedule(CRIACAO, List.of(new TaskSchedule.Rule(CRIACAO, dias)));
    }

    private static Set<LocalDate> dias(String... datas) {
        Set<LocalDate> set = new HashSet<>();
        for (String d : datas) {
            set.add(LocalDate.parse(d));
        }
        return set;
    }

    // T02 T1 (CA1)
    @Test
    void diaQueNaoEDevidoNaoQuebraNemSoma() {
        TaskSchedule academia = agenda(EnumSet.of(WeekDay.MON, WeekDay.WED, WeekDay.FRI));

        assertThat(TaskStats.streak(academia, dias("2026-10-05", "2026-10-07"), HOJE)).isEqualTo(2);
        // A sexta 02/10 não foi feita: a sequência para ali
        assertThat(TaskStats.streak(academia, dias("2026-10-05", "2026-10-07", "2026-10-01"), HOJE)).isEqualTo(2);
    }

    // T02 T2 (CA2)
    @Test
    void hojePendenteNaoZeraOntemNaoFeitoZera() {
        TaskSchedule todoDia = agenda(TaskSchedule.EVERY_DAY);
        Set<LocalDate> ateOntem = dias("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07");

        assertThat(TaskStats.streak(todoDia, ateOntem, HOJE)).isEqualTo(7);

        Set<LocalDate> semOntem = new HashSet<>(ateOntem);
        semOntem.remove(LocalDate.parse("2026-10-07"));
        assertThat(TaskStats.streak(todoDia, semOntem, HOJE)).isZero();

        Set<LocalDate> comHoje = new HashSet<>(ateOntem);
        comHoje.add(HOJE);
        assertThat(TaskStats.streak(todoDia, comHoje, HOJE)).isEqualTo(8);
    }

    @Test
    void porcentagemContaSoDiasDevidosEHojeSoSeFeito() {
        TaskSchedule todoDia = agenda(TaskSchedule.EVERY_DAY);
        // 7 dias devidos antes de hoje (01 a 07), 5 feitos; hoje pendente não entra
        assertThat(TaskStats.rate(todoDia, dias("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-06", "2026-10-07"), HOJE))
                .isEqualByComparingTo("71.42");
        // Hoje feito entra: 6 de 8
        assertThat(TaskStats.rate(todoDia, dias("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-06", "2026-10-07", "2026-10-08"), HOJE))
                .isEqualByComparingTo("75.00");
        // Criada hoje e pendente: nenhum dia devido
        assertThat(TaskStats.rate(new TaskSchedule(HOJE, List.of()), Set.of(), HOJE)).isNull();
    }
}
