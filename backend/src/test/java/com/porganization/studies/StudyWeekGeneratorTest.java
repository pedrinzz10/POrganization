package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import com.porganization.studies.StudyWeekGenerator.Slot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class StudyWeekGeneratorTest {

    // Quarta 07/10/2026; semana de 05/10 a 11/10
    private static final LocalDate HOJE = LocalDate.parse("2026-10-07");
    private static final LocalDate SEG = LocalDate.parse("2026-10-05");
    private final UUID java = UUID.randomUUID();
    private final UUID ingles = UUID.randomUUID();
    private final UUID calculo = UUID.randomUUID();

    private SubjectGoal meta(UUID id, int prioridade, int porSemana, DayOfWeek... dias) {
        return new SubjectGoal(id, id.toString(), null, prioridade, porSemana, 50, Set.of(dias));
    }

    private static List<LocalDate> dias(List<Slot> slots, UUID materia) {
        return slots.stream().filter(s -> s.subjectId().equals(materia)).map(Slot::day).toList();
    }

    // E15 T2 (CA1)
    @RepeatedTest(20)
    void respeitaMetaDiasDeEstudoEUmaPorDia() {
        List<Slot> slots = StudyWeekGenerator.generate(HOJE, SEG, List.of(
                meta(java, 1, 3),
                meta(ingles, 2, 2, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                meta(calculo, 3, 9)), Map.of(java, 1L), Set.of(), new Random());

        // Java: meta 3, já fez 1 → 2, de hoje (qua) a domingo
        assertThat(dias(slots, java)).hasSize(2).allSatisfy(d -> assertThat(d).isAfterOrEqualTo(HOJE));
        assertThat(dias(slots, ingles)).hasSize(2)
                .allSatisfy(d -> assertThat(d.getDayOfWeek()).isIn(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        // Cálculo pede 9, mas só cabem 5 dias (qua a dom), uma por dia
        assertThat(dias(slots, calculo)).hasSize(5).doesNotHaveDuplicates();
        // Sorteio entre os dias menos carregados: nada de amontoar (9 aulas em 5 dias, no máximo 3 num dia)
        Map<LocalDate, Long> porDia = slots.stream().collect(Collectors.groupingBy(Slot::day, Collectors.counting()));
        assertThat(porDia.values()).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(3));
    }

    // E15 T2 (CA1)
    @Test
    void cadaSorteioPodeDarUmaSemanaDiferenteEHojeSaiSeJaTeveAula() {
        Set<List<LocalDate>> semanas = new HashSet<>();
        for (int seed = 0; seed < 30; seed++) {
            List<Slot> slots = StudyWeekGenerator.generate(HOJE, SEG, List.of(meta(java, 1, 2)), Map.of(java, 1L), Set.of(java),
                    new SplittableRandom(seed));
            assertThat(dias(slots, java)).hasSize(1).allSatisfy(d -> assertThat(d).isAfter(HOJE));
            semanas.add(dias(slots, java));
        }
        assertThat(semanas.size()).isGreaterThan(1);

        // Semana futura: conta a meta inteira, de segunda a domingo
        List<Slot> proxima = StudyWeekGenerator.generate(HOJE, SEG.plusWeeks(1), List.of(meta(java, 1, 7)), Map.of(java, 5L),
                Set.of(java), new Random(1));
        assertThat(dias(proxima, java)).hasSize(7);
    }
}
