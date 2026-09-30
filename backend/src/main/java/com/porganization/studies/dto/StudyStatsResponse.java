package com.porganization.studies.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estatísticas de estudo de from a to. Minutos contam só sessões terminadas (FINISHED).
 *
 * @param subjects por matéria; sessionsThisWeek é da semana corrente, independente do intervalo
 * @param weeks    minutos por semana (segunda a domingo), incluindo semanas sem estudo
 * @param lessons  aulas estudadas no intervalo, da mais recente para a mais antiga
 */
public record StudyStatsResponse(
        LocalDate from,
        LocalDate to,
        long totalMinutes,
        long reviewsDone,
        List<SubjectStats> subjects,
        List<WeekMinutes> weeks,
        List<LessonEntry> lessons) {

    public record SubjectStats(UUID subjectId, String name, String color, long minutes, long lessons, long reviews,
            long sessionsThisWeek, int sessionsPerWeek) {
    }

    public record WeekMinutes(LocalDate weekStart, long minutes) {
    }

    public record LessonEntry(UUID id, UUID subjectId, String title, String notes, Instant studiedAt,
            int durationMinutes) {
    }
}
