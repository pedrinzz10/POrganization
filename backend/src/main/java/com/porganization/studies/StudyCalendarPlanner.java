package com.porganization.studies;

import com.porganization.studies.DailyStudyPlanner.DueReview;
import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agenda de estudos por dia (lógica pura, como o DailyStudyPlanner):
 * <ul>
 * <li>Até hoje: as sessões concluídas em cada dia.</li>
 * <li>Hoje: as revisões atrasadas (vencidas antes de hoje) e as que vencem hoje.</li>
 * <li>Depois de hoje: as revisões no dia em que vencem.</li>
 * <li>Aulas: a meta semanal de cada matéria (sessions_per_week) menos as aulas já feitas na semana,
 * espalhadas pelos dias que faltam da semana (hoje até domingo; nas semanas seguintes, segunda a
 * domingo), por prioridade, no dia menos carregado perto de um espaçamento regular. Uma matéria não
 * repete no mesmo dia, nem hoje se já teve aula hoje.</li>
 * </ul>
 * É uma previsão: conforme as sessões acontecem, a distribuição do resto da semana muda.
 */
public final class StudyCalendarPlanner {

    private StudyCalendarPlanner() {
    }

    public enum Kind {
        /** Sessão concluída (aula ou revisão). */
        DONE,
        /** Revisão agendada pela repetição espaçada. */
        REVIEW,
        /** Aula sugerida para cumprir a meta da semana. */
        LESSON
    }

    /** Sessão concluída, já no dia do fuso do usuário. */
    public record DoneSession(LocalDate date, UUID subjectId, String subjectName, String color, SessionType type, String title,
            int minutes) {
    }

    public record Item(Kind kind, UUID subjectId, String subjectName, String color, String title, int minutes,
            SessionType sessionType, boolean overdue) {
    }

    public record Day(LocalDate date, List<Item> items) {
    }

    /**
     * @param reviews revisões com vencimento até {@code to} (as de antes de hoje entram em hoje como atrasadas)
     * @param lessonsThisWeek aulas concluídas na semana de hoje, por matéria
     * @param lessonToday matérias que já tiveram aula hoje
     */
    public static List<Day> plan(LocalDate today, LocalDate from, LocalDate to, List<DoneSession> done, List<DueReview> reviews,
            List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday) {
        Map<LocalDate, List<Item>> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }

        done.stream()
                .filter(s -> byDay.containsKey(s.date()) && !s.date().isAfter(today))
                .forEach(s -> byDay.get(s.date()).add(new Item(Kind.DONE, s.subjectId(), s.subjectName(), s.color(), s.title(),
                        s.minutes(), s.type(), false)));

        Map<UUID, SubjectGoal> goalById = new HashMap<>();
        goals.forEach(g -> goalById.put(g.subjectId(), g));
        reviews.stream()
                .sorted(Comparator.comparing(DueReview::dueDate))
                .forEach(r -> {
                    boolean overdue = r.dueDate().isBefore(today);
                    LocalDate day = overdue ? today : r.dueDate();
                    if (byDay.containsKey(day)) {
                        SubjectGoal g = goalById.get(r.subjectId());
                        byDay.get(day).add(new Item(Kind.REVIEW, r.subjectId(), r.subjectName(), g == null ? null : g.color(),
                                r.lessonTitle(), r.reviewMinutes(), SessionType.REVIEW, overdue));
                    }
                });

        distributeLessons(today, from, to, goals, lessonsThisWeek, lessonToday, byDay);

        return byDay.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue())).toList();
    }

    private static void distributeLessons(LocalDate today, LocalDate from, LocalDate to, List<SubjectGoal> goals,
            Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday, Map<LocalDate, List<Item>> byDay) {
        LocalDate start = from.isAfter(today) ? from : today;
        if (start.isAfter(to)) {
            return;
        }
        List<SubjectGoal> byPriority = goals.stream().sorted(Comparator.comparingInt(SubjectGoal::priorityOrder)).toList();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // Semana a semana: a distribuição olha a semana inteira, mesmo que a consulta corte no meio
        for (LocalDate monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); !monday.isAfter(to);
                monday = monday.plusWeeks(1)) {
            boolean currentWeek = monday.equals(thisMonday);
            LocalDate first = currentWeek ? today : monday;
            List<LocalDate> days = new ArrayList<>();
            for (LocalDate d = first; d.isBefore(monday.plusDays(7)); d = d.plusDays(1)) {
                days.add(d);
            }
            Map<LocalDate, Integer> load = new HashMap<>();
            for (SubjectGoal g : byPriority) {
                long needed = g.sessionsPerWeek() - (currentWeek ? lessonsThisWeek.getOrDefault(g.subjectId(), 0L) : 0L);
                List<LocalDate> candidates = currentWeek && lessonToday.contains(g.subjectId())
                        ? days.stream().filter(d -> !d.equals(today)).toList()
                        : days;
                for (LocalDate day : spread(candidates, (int) Math.min(Math.max(needed, 0), candidates.size()), load)) {
                    load.merge(day, 1, Integer::sum);
                    if (byDay.containsKey(day)) {
                        byDay.get(day).add(new Item(Kind.LESSON, g.subjectId(), g.subjectName(), g.color(), null,
                                g.lessonMinutes(), SessionType.LESSON, false));
                    }
                }
            }
        }
    }

    /**
     * Escolhe {@code count} dias distintos: para cada um, o alvo é o espaçamento regular
     * (meio de cada fatia) e ganha o dia menos carregado mais perto do alvo.
     */
    static List<LocalDate> spread(List<LocalDate> days, int count, Map<LocalDate, Integer> load) {
        List<LocalDate> chosen = new ArrayList<>();
        Set<LocalDate> used = new HashSet<>();
        int n = days.size();
        for (int j = 0; j < count; j++) {
            double target = (j + 0.5) * n / count - 0.5;
            LocalDate best = null;
            double bestScore = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                LocalDate d = days.get(i);
                if (used.contains(d)) {
                    continue;
                }
                // A carga pesa mais que a distância: dias vazios primeiro, depois o mais perto do alvo
                double score = load.getOrDefault(d, 0) * n + Math.abs(i - target);
                if (score < bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
            used.add(best);
            chosen.add(best);
        }
        chosen.sort(Comparator.naturalOrder());
        return chosen;
    }
}
