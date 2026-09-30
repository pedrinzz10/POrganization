package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.studies.DailyStudyPlanner.DueReview;
import com.porganization.studies.DailyStudyPlanner.Plan;
import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyStudyPlannerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 1);

    private static DueReview revisao(String titulo, String vencimento) {
        return new DueReview(UUID.randomUUID(), UUID.randomUUID(), "Java", titulo, LocalDate.parse(vencimento), 25);
    }

    private static SubjectGoal meta(String nome, int prioridade, int sessoesPorSemana) {
        return new SubjectGoal(UUID.randomUUID(), nome, null, prioridade, sessoesPorSemana, 50);
    }

    // E08 T1 (CA1)
    @Test
    void revisoesVencidasVemAntesEDaMaisAtrasada() {
        List<DueReview> vencidas = List.of(revisao("Lambdas", "2026-09-30"), revisao("Streams", "2026-09-28"));
        SubjectGoal ingles = meta("Inglês", 1, 2);

        Plan plano = DailyStudyPlanner.plan(HOJE, vencidas, List.of(ingles), Map.of());

        assertThat(plano.reviews()).extracting(r -> r.lessonTitle()).containsExactly("Streams", "Lambdas");
        assertThat(plano.reviews()).extracting(r -> r.daysOverdue()).containsExactly(3L, 1L);
        assertThat(plano.lessons()).extracting(l -> l.subjectName()).containsExactly("Inglês");
    }

    @Test
    void revisaoDeHojeNaoEstaAtrasada() {
        Plan plano = DailyStudyPlanner.plan(HOJE, List.of(revisao("Hoje", "2026-10-01")), List.of(), Map.of());
        assertThat(plano.reviews().getFirst().daysOverdue()).isZero();
    }

    // E08 T2 (CA2)
    @Test
    void materiaQueJaCumpriuAMetaNaoAparece() {
        SubjectGoal java = meta("Java", 1, 2);
        SubjectGoal ingles = meta("Inglês", 2, 2);

        Plan plano = DailyStudyPlanner.plan(HOJE, List.of(), List.of(java, ingles), Map.of(java.subjectId(), 2L, ingles.subjectId(), 1L));

        assertThat(plano.lessons()).extracting(l -> l.subjectName()).containsExactly("Inglês");
        assertThat(plano.lessons().getFirst().doneThisWeek()).isEqualTo(1);
        assertThat(plano.lessons().getFirst().sessionsPerWeek()).isEqualTo(2);
    }

    @Test
    void materiaComMetaZeroNuncaESugerida() {
        Plan plano = DailyStudyPlanner.plan(HOJE, List.of(), List.of(meta("Pausada", 1, 0)), Map.of());
        assertThat(plano.lessons()).isEmpty();
    }

    // E08 (CA3): aulas seguem a prioridade, mesmo que a lista venha fora de ordem
    @Test
    void aulasSeguemAOrdemDePrioridade() {
        Plan plano = DailyStudyPlanner.plan(HOJE, List.of(), List.of(meta("Java", 2, 2), meta("Inglês", 1, 2)), Map.of());
        assertThat(plano.lessons()).extracting(l -> l.subjectName()).containsExactly("Inglês", "Java");
        assertThat(plano.lessons().getFirst().suggestedMinutes()).isEqualTo(50);
    }
}
