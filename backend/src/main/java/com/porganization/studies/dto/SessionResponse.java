package com.porganization.studies.dto;

import com.porganization.studies.SessionStatus;
import com.porganization.studies.SessionType;
import java.time.Instant;
import java.util.UUID;

/**
 * Estado do timer. elapsedSeconds é calculado no servidor (descontando pausas), para o
 * frontend retomar a contagem certa depois de recarregar a página.
 */
public record SessionResponse(
        UUID id,
        UUID subjectId,
        String subjectName,
        UUID lessonId,
        SessionType type,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        int pausedSeconds,
        Instant pausedAt,
        long elapsedSeconds,
        int plannedMinutes) {
}
