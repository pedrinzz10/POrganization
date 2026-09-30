package com.porganization.studies;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface StudySessionRepository extends Repository<StudySession, UUID> {

    StudySession save(StudySession session);

    StudySession saveAndFlush(StudySession session);

    Optional<StudySession> findByIdAndUserId(UUID id, UUID userId);

    /** A sessão rodando ou pausada do usuário (o índice único garante no máximo uma). */
    @Query("select s from StudySession s where s.userId = :userId"
            + " and s.status in (com.porganization.studies.SessionStatus.RUNNING, com.porganization.studies.SessionStatus.PAUSED)")
    Optional<StudySession> findActive(UUID userId);

    List<StudySession> findByUserIdAndStatusAndStartedAtBetween(UUID userId, SessionStatus status, Instant from,
            Instant to);
}
