package com.porganization.studies.dto;

import com.porganization.studies.Subject;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        String color,
        int priorityOrder,
        int sessionsPerWeek,
        int lessonMinutes,
        boolean archived,
        List<TagResponse> tags) {

    public static SubjectResponse from(Subject s) {
        List<TagResponse> tags = s.getTags().stream().map(TagResponse::from)
                .sorted(Comparator.comparing(TagResponse::name, String.CASE_INSENSITIVE_ORDER)).toList();
        return new SubjectResponse(s.getId(), s.getName(), s.getColor(), s.getPriorityOrder(), s.getSessionsPerWeek(),
                s.getLessonMinutes(), s.isArchived(), tags);
    }
}
