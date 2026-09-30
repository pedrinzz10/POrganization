package com.porganization.studies;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta o plano de estudo do dia. Lógica pura: recebe os dados já buscados e decide a ordem.
 * <ol>
 * <li>Revisões vencidas (due_date <= hoje), da mais atrasada para a mais recente.</li>
 * <li>Aulas das matérias que ainda não cumpriram a meta de sessões da semana, pela prioridade.</li>
 * </ol>
 */
public final class DailyStudyPlanner {

    private DailyStudyPlanner() {
    }

    /** Revisão vencida, como vem do banco. */
    public record DueReview(UUID lessonId, UUID subjectId, String subjectName, String lessonTitle, LocalDate dueDate,
            int reviewMinutes) {
    }

    /** Meta semanal de uma matéria ativa. */
    public record SubjectGoal(UUID subjectId, String subjectName, String color, int priorityOrder, int sessionsPerWeek,
            int lessonMinutes) {
    }

    public record ReviewSuggestion(UUID lessonId, UUID subjectId, String subjectName, String lessonTitle,
            LocalDate dueDate, long daysOverdue, int reviewMinutes) {
    }

    public record LessonSuggestion(UUID subjectId, String subjectName, String color, int priorityOrder,
            int suggestedMinutes, long doneThisWeek, int sessionsPerWeek) {
    }

    public record Plan(List<ReviewSuggestion> reviews, List<LessonSuggestion> lessons) {
    }

    /**
     * @param lessonsThisWeek aulas (sessões LESSON terminadas) por matéria na semana corrente
     */
    public static Plan plan(LocalDate today, List<DueReview> dueReviews, List<SubjectGoal> subjects,
            Map<UUID, Long> lessonsThisWeek) {
        List<ReviewSuggestion> reviews = dueReviews.stream()
                .filter(r -> !r.dueDate().isAfter(today))
                // ordenação estável: empate no vencimento mantém a ordem de chegada (a mais antiga primeiro)
                .sorted(Comparator.comparing(DueReview::dueDate))
                .map(r -> new ReviewSuggestion(r.lessonId(), r.subjectId(), r.subjectName(), r.lessonTitle(), r.dueDate(),
                        ChronoUnit.DAYS.between(r.dueDate(), today), r.reviewMinutes()))
                .toList();

        List<LessonSuggestion> lessons = subjects.stream()
                .sorted(Comparator.comparingInt(SubjectGoal::priorityOrder))
                .filter(s -> lessonsThisWeek.getOrDefault(s.subjectId(), 0L) < s.sessionsPerWeek())
                .map(s -> new LessonSuggestion(s.subjectId(), s.subjectName(), s.color(), s.priorityOrder(),
                        s.lessonMinutes(), lessonsThisWeek.getOrDefault(s.subjectId(), 0L), s.sessionsPerWeek()))
                .toList();

        return new Plan(reviews, lessons);
    }
}
