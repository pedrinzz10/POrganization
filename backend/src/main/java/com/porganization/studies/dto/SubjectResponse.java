package com.porganization.studies.dto;

import com.porganization.commitments.recurrence.WeekDay;
import com.porganization.studies.LessonMode;
import com.porganization.studies.Subject;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        String color,
        int priorityOrder,
        int sessionsPerWeek,
        int lessonMinutes,
        boolean archived,
        List<TagResponse> tags,
        /** Dias em que a matéria pode ter aula; vazio = qualquer dia. */
        Set<WeekDay> studyDays,
        LessonMode lessonMode,
        /** Aulas definidas (E14): total e quantas já foram estudadas; 0 e 0 na matéria livre. */
        int plannedTotal,
        int plannedDone) {

    /** Contagem das aulas definidas de uma matéria. */
    public record PlannedCount(int total, int done) {
        public static final PlannedCount NONE = new PlannedCount(0, 0);
    }

    public static SubjectResponse from(Subject s) {
        return from(s, PlannedCount.NONE);
    }

    public static SubjectResponse from(Subject s, PlannedCount planned) {
        List<TagResponse> tags = s.getTags().stream().map(TagResponse::from)
                .sorted(Comparator.comparing(TagResponse::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new SubjectResponse(s.getId(), s.getName(), s.getColor(), s.getPriorityOrder(), s.getSessionsPerWeek(),
                s.getLessonMinutes(), s.isArchived(), tags, s.getStudyDays(), s.getLessonMode(), planned.total(),
                planned.done());
    }
}
