package com.porganization.studies.dto;

import com.porganization.studies.SessionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Iniciar o timer. Para REVIEW, lessonId é a aula que será revisada. */
public record StartSessionRequest(@NotNull UUID subjectId, @NotNull SessionType type, UUID lessonId) {
}
