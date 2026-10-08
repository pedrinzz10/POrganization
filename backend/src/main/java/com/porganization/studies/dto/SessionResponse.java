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
        int plannedMinutes,
        /** Aula definida (E16) desta sessão de aula: o título vem pronto para terminar. */
        UUID plannedLessonId,
        String plannedLessonTitle) {
}
