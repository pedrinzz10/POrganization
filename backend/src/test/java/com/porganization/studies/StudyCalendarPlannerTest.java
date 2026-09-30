package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.studies.DailyStudyPlanner.DueReview;
import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import com.porganization.studies.StudyCalendarPlanner.Day;
import com.porganization.studies.StudyCalendarPlanner.DoneSession;
import com.porganization.studies.StudyCalendarPlanner.Kind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudyCalendarPlannerTest {

    // Quarta 07/10/2026; semana de 05/10 (seg) a 11/10 (dom)
    private static final LocalDate HOJE = LocalDate.parse("2026-10-07");
    private static final LocalDate SEG = LocalDate.parse("2026-10-05");
    private static final LocalDate DOM = LocalDate.parse("2026-10-11");
    private final UUID java = UUID.randomUUID();
    private final UUID ingles = UUID.randomUUID();

    private SubjectGoal meta(UUID id, String nome, int prioridade, int porSemana) {
        return new SubjectGoal(id, nome, "#123456", prioridade, porSemana, 50);
    }

    private static List<String> aulas(List<Day> dias, String materia) {
        return dias.stream()
                .filter(d -> d.items().stream().anyMatch(i -> i.kind() == Kind.LESSON && i.subjectName().equals(materia)))
                .map(d -> d.date().toString())
                .toList();
    }

    @Test
    void passadoMostraOQueFoiEstudadoEHojeAsRevisoesAtrasadas() {
        List<Day> dias = StudyCalendarPlanner.plan(HOJE, SEG, DOM,
                List.of(new DoneSession(SEG, java, "Java", "#123456", SessionType.LESSON, "Streams", 45)),
                List.of(new DueReview(UUID.randomUUID(), java, "Java", "Lambdas", LocalDate.parse("2026-10-06"), 10),
                        new DueReview(UUID.randomUUID(), java, "Java", "Records", LocalDate.parse("2026-10-09"), 10)),
                List.of(), Map.of(), Set.of());

        assertThat(dias).hasSize(7);
        assertThat(dias.get(0).items()).singleElement()
                .satisfies(i -> {
                    assertThat(i.kind()).isEqualTo(Kind.DONE);
                    assertThat(i.title()).isEqualTo("Streams");
                    assertThat(i.minutes()).isEqualTo(45);
                });
        // Vencida ontem: aparece hoje como atrasada, não no dia 06
        assertThat(dias.get(1).items()).isEmpty();
        assertThat(dias.get(2).items()).singleElement()
                .satisfies(i -> {
                    assertThat(i.title()).isEqualTo("Lambdas");
                    assertThat(i.overdue()).isTrue();
                });
        assertThat(dias.get(4).items()).singleElement().satisfies(i -> assertThat(i.overdue()).isFalse());
    }

    // E12 T1 (CA2)
    @Test
    void aulasQueFaltamNaSemanaSeEspalhamDeHojeAteDomingoSemRepetirODia() {
        // Java: meta 3, já fez 1 → faltam 2 em qua..dom (5 dias). Inglês: meta 2 → 2
        List<Day> dias = StudyCalendarPlanner.plan(HOJE, SEG, DOM, List.of(), List.of(),
                List.of(meta(java, "Java", 1, 3), meta(ingles, "Inglês", 2, 2)), Map.of(java, 1L), Set.of());

        List<String> diasJava = aulas(dias, "Java");
        List<String> diasIngles = aulas(dias, "Inglês");
        assertThat(diasJava).hasSize(2).doesNotHaveDuplicates().allMatch(d -> d.compareTo("2026-10-07") >= 0);
        assertThat(diasIngles).hasSize(2).doesNotHaveDuplicates().allMatch(d -> d.compareTo("2026-10-07") >= 0);
        // Com 5 dias e 4 aulas, nenhum dia recebe duas
        assertThat(diasJava).doesNotContainAnyElementsOf(diasIngles);
        // Nada no passado
        assertThat(dias.get(0).items()).isEmpty();
        assertThat(dias.get(1).items()).isEmpty();
    }

    @Test
    void quemJaTeveAulaHojeFicaParaOsProximosDiasEMetaCumpridaNaoAparece() {
        List<Day> dias = StudyCalendarPlanner.plan(HOJE, HOJE, DOM, List.of(), List.of(),
                List.of(meta(java, "Java", 1, 2), meta(ingles, "Inglês", 2, 1)), Map.of(java, 1L, ingles, 1L), Set.of(java));

        assertThat(aulas(dias, "Java")).hasSize(1).doesNotContain("2026-10-07");
        assertThat(aulas(dias, "Inglês")).isEmpty();
    }

    @Test
    void semanasSeguintesRecebemAMetaInteira() {
        LocalDate seg = LocalDate.parse("2026-10-12");
        List<Day> dias = StudyCalendarPlanner.plan(HOJE, seg, seg.plusDays(6), List.of(), List.of(),
                List.of(meta(java, "Java", 1, 3)), Map.of(java, 3L), Set.of());

        // Três dias espaçados na semana de 12 a 18: ter, qui e sáb
        assertThat(aulas(dias, "Java")).containsExactly("2026-10-13", "2026-10-15", "2026-10-17");
    }
}
