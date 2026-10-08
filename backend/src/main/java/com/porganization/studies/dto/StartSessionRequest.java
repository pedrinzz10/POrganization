package com.porganization.studies.dto;

import com.porganization.studies.SessionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Iniciar o timer. Para REVIEW, lessonId é a aula que será revisada. Para LESSON numa matéria com
 * aulas definidas, plannedLessonId escolhe a aula da lista (sem ele, vai a próxima pendente).
 */
public record StartSessionRequest(@NotNull UUID subjectId, @NotNull SessionType type, UUID lessonId, UUID plannedLessonId) {

    public StartSessionRequest(UUID subjectId, SessionType type, UUID lessonId) {
        this(subjectId, type, lessonId, null);
    }
}
