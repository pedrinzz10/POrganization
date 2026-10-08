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
 * repete no mesmo dia, nem hoje se já teve aula hoje, e só cai nos dias de estudo da matéria.</li>
 * <li>Aulas fixadas (o usuário arrastou na agenda) ficam no dia escolhido e contam na meta; o
 * resto é que se espalha.</li>
 * <li>Semana com plano (E15): as aulas são as do plano, no dia escolhido; nada é espalhado.</li>
 * <li>Matéria com aulas definidas (E14): cada aula da agenda, em ordem de data, recebe a próxima
 * aula pendente da lista.</li>
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

    /**
     * pinned: aula fixada pelo usuário num dia (não muda com a redistribuição).
     * plannedLessonId: na aula sugerida de matéria com aulas definidas, qual aula da lista é (title é o nome dela).
     */
    public record Item(Kind kind, UUID subjectId, String subjectName, String color, String title, int minutes,
            SessionType sessionType, boolean overdue, boolean pinned, UUID plannedLessonId) {

        public Item(Kind kind, UUID subjectId, String subjectName, String color, String title, int minutes,
                SessionType sessionType, boolean overdue, boolean pinned) {
            this(kind, subjectId, subjectName, color, title, minutes, sessionType, overdue, pinned, null);
        }
    }

    /** planned: a semana do dia tem plano montado pelo usuário (E15). */
    public record Day(LocalDate date, List<Item> items, boolean planned) {

        public Day(LocalDate date, List<Item> items) {
            this(date, items, false);
        }
    }

    /** Semanas com plano (pela segunda-feira) e as matérias de cada dia, na ordem em que entraram. */
    public record WeekPlans(Set<LocalDate> weeks, Map<LocalDate, List<UUID>> slots) {
        public static final WeekPlans NONE = new WeekPlans(Set.of(), Map.of());
    }

    /** Aula definida ainda não estudada, na ordem do curso. */
    public record PendingLesson(UUID id, String title) {
    }

    /**
     * @param reviews revisões com vencimento até {@code to} (as de antes de hoje entram em hoje como atrasadas)
     * @param lessonsThisWeek aulas concluídas na semana de hoje, por matéria
     * @param lessonToday matérias que já tiveram aula hoje
     */
    public static List<Day> plan(LocalDate today, LocalDate from, LocalDate to, List<DoneSession> done, List<DueReview> reviews,
            List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday) {
        return plan(today, from, to, done, reviews, goals, lessonsThisWeek, lessonToday, Map.of());
    }

    /** @param pins dias em que o usuário fixou aula, por matéria */
    public static List<Day> plan(LocalDate today, LocalDate from, LocalDate to, List<DoneSession> done, List<DueReview> reviews,
            List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday, Map<UUID, List<LocalDate>> pins) {
        return plan(today, from, to, done, reviews, goals, lessonsThisWeek, lessonToday, pins, WeekPlans.NONE, Map.of());
    }

    public static List<Day> plan(LocalDate today, LocalDate from, LocalDate to, List<DoneSession> done, List<DueReview> reviews,
            List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday, Map<UUID, List<LocalDate>> pins,
            WeekPlans plans, Map<UUID, List<PendingLesson>> pending) {
        return plan(today, from, to, done, reviews, goals, lessonsThisWeek, lessonToday, pins, plans, pending, Set.of());
    }

    /**
     * @param plans semanas com plano do usuário e as aulas de cada dia
     * @param pending aulas definidas pendentes por matéria, na ordem do curso
     * @param onHold matérias concluídas ou bloqueadas por pré-requisito (E17): fora da previsão automática,
     *        mas aparecem se o usuário pôs no plano
     */
    public static List<Day> plan(LocalDate today, LocalDate from, LocalDate to, List<DoneSession> done, List<DueReview> reviews,
            List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday, Map<UUID, List<LocalDate>> pins,
            WeekPlans plans, Map<UUID, List<PendingLesson>> pending, Set<UUID> onHold) {
        Map<LocalDate, List<Item>> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }

        done.stream()
                .filter(s -> byDay.containsKey(s.date()) && !s.date().isAfter(today))
                .forEach(s -> byDay.get(s.date()).add(new Item(Kind.DONE, s.subjectId(), s.subjectName(), s.color(), s.title(),
                        s.minutes(), s.type(), false, false)));

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
                                r.lessonTitle(), r.reviewMinutes(), SessionType.REVIEW, overdue, false));
                    }
                });

        distributeLessons(today, from, to, goals, lessonsThisWeek, lessonToday, pins, plans, onHold, byDay);
        namePlannedLessons(pending, byDay);

        return byDay.entrySet().stream()
                .map(e -> new Day(e.getKey(), e.getValue(), plans.weeks().contains(mondayOf(e.getKey()))))
                .toList();
    }

    /** Em ordem de data, a n-ésima aula sugerida da matéria é a n-ésima aula pendente da lista. */
    private static void namePlannedLessons(Map<UUID, List<PendingLesson>> pending, Map<LocalDate, List<Item>> byDay) {
        Map<UUID, Integer> next = new HashMap<>();
        byDay.values().forEach(items -> items.replaceAll(item -> {
            List<PendingLesson> lessons = pending.get(item.subjectId());
            if (item.kind() != Kind.LESSON || lessons == null) {
                return item;
            }
            int index = next.merge(item.subjectId(), 1, Integer::sum) - 1;
            if (index >= lessons.size()) {
                return item;
            }
            PendingLesson lesson = lessons.get(index);
            return new Item(item.kind(), item.subjectId(), item.subjectName(), item.color(), lesson.title(), item.minutes(),
                    item.sessionType(), item.overdue(), item.pinned(), lesson.id());
        }));
    }

    static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static void distributeLessons(LocalDate today, LocalDate from, LocalDate to, List<SubjectGoal> goals,
            Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday, Map<UUID, List<LocalDate>> pins, WeekPlans plans,
            Set<UUID> onHold, Map<LocalDate, List<Item>> byDay) {
        LocalDate start = from.isAfter(today) ? from : today;
        if (start.isAfter(to)) {
            return;
        }
        List<SubjectGoal> byPriority = goals.stream()
                .filter(g -> !onHold.contains(g.subjectId()))
                .sorted(Comparator.comparingInt(SubjectGoal::priorityOrder))
                .toList();
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
            if (plans.weeks().contains(monday)) {
                // Semana com plano: só o que o usuário montou (a de hoje sai se a matéria já teve aula hoje)
                Map<UUID, SubjectGoal> goalById = new HashMap<>();
                goals.forEach(g -> goalById.put(g.subjectId(), g));
                for (LocalDate day : days) {
                    for (UUID subjectId : plans.slots().getOrDefault(day, List.of())) {
                        SubjectGoal g = goalById.get(subjectId);
                        if (g != null && !(currentWeek && day.equals(today) && lessonToday.contains(subjectId))) {
                            place(g, day, false, load, byDay);
                        }
                    }
                }
                continue;
            }
            Map<UUID, Long> needed = new HashMap<>();
            Map<UUID, Set<LocalDate>> pinned = new HashMap<>();
            // 1º as fixadas (de hoje em diante, até a meta): ocupam o dia antes da distribuição automática
            for (SubjectGoal g : byPriority) {
                long left = Math.max(0, g.sessionsPerWeek() - (currentWeek ? lessonsThisWeek.getOrDefault(g.subjectId(), 0L) : 0L));
                List<LocalDate> fixed = pins.getOrDefault(g.subjectId(), List.of()).stream()
                        .filter(days::contains)
                        .sorted()
                        .limit(left)
                        .toList();
                pinned.put(g.subjectId(), new HashSet<>(fixed));
                needed.put(g.subjectId(), left - fixed.size());
                fixed.forEach(day -> place(g, day, true, load, byDay));
            }
            // 2º o que falta, nos dias de estudo da matéria
            for (SubjectGoal g : byPriority) {
                Set<LocalDate> fixed = pinned.get(g.subjectId());
                List<LocalDate> candidates = days.stream()
                        .filter(g::allows)
                        .filter(d -> !fixed.contains(d))
                        .filter(d -> !(currentWeek && d.equals(today) && lessonToday.contains(g.subjectId())))
                        .toList();
                int count = (int) Math.min(needed.get(g.subjectId()), candidates.size());
                spread(candidates, count, load).forEach(day -> place(g, day, false, load, byDay));
            }
        }
    }

    private static void place(SubjectGoal g, LocalDate day, boolean pinned, Map<LocalDate, Integer> load,
            Map<LocalDate, List<Item>> byDay) {
        load.merge(day, 1, Integer::sum);
        if (byDay.containsKey(day)) {
            byDay.get(day).add(new Item(Kind.LESSON, g.subjectId(), g.subjectName(), g.color(), null, g.lessonMinutes(),
                    SessionType.LESSON, false, pinned));
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
