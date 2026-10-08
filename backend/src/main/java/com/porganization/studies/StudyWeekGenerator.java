package com.porganization.studies;

import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * "Gerar semana" (E15), lógica pura. Cada matéria, por prioridade, recebe a meta semanal menos as
 * aulas já feitas na semana; os dias são sorteados entre os dias de estudo dela que faltam na
 * semana (de hoje em diante), sem repetir a matéria no mesmo dia. O sorteio só escolhe entre os
 * dias menos carregados, para não amontoar tudo num dia.
 */
public final class StudyWeekGenerator {

    private StudyWeekGenerator() {
    }

    public record Slot(UUID subjectId, LocalDate day) {
    }

    /**
     * @param monday segunda-feira da semana a gerar (a de hoje ou uma futura)
     * @param lessonsThisWeek aulas feitas na semana de hoje, por matéria (só conta na semana de hoje)
     * @param lessonToday matérias que já tiveram aula hoje
     */
    public static List<Slot> generate(LocalDate today, LocalDate monday, List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek,
            Set<UUID> lessonToday, RandomGenerator random) {
        boolean currentWeek = !today.isBefore(monday) && today.isBefore(monday.plusDays(7));
        LocalDate first = currentWeek ? today : monday;
        List<LocalDate> week = first.datesUntil(monday.plusDays(7)).toList();
        Map<LocalDate, Integer> load = new HashMap<>();
        List<Slot> slots = new ArrayList<>();

        for (SubjectGoal g : goals.stream().sorted(Comparator.comparingInt(SubjectGoal::priorityOrder)).toList()) {
            long left = Math.max(0, g.sessionsPerWeek() - (currentWeek ? lessonsThisWeek.getOrDefault(g.subjectId(), 0L) : 0L));
            List<LocalDate> candidates = new ArrayList<>(week.stream()
                    .filter(g::allows)
                    .filter(d -> !(currentWeek && d.equals(today) && lessonToday.contains(g.subjectId())))
                    .toList());
            for (long i = 0; i < left && !candidates.isEmpty(); i++) {
                int min = candidates.stream().mapToInt(d -> load.getOrDefault(d, 0)).min().orElseThrow();
                List<LocalDate> lightest = candidates.stream().filter(d -> load.getOrDefault(d, 0) == min).toList();
                LocalDate day = lightest.get(random.nextInt(lightest.size()));
                candidates.remove(day);
                load.merge(day, 1, Integer::sum);
                slots.add(new Slot(g.subjectId(), day));
            }
        }
        slots.sort(Comparator.comparing(Slot::day));
        return slots;
    }
}
