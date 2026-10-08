package com.porganization.studies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Uma sessão de estudo com timer. As transições (pausar, retomar, terminar) recebem o instante
 * de fora, então a regra de tempo é testável sem relógio real.
 */
@Entity
@Table(name = "study_sessions")
public class StudySession {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    /** Aula definida (E14) que esta sessão de aula estuda, se a matéria tem lista. */
    @Column(name = "planned_lesson_id")
    private UUID plannedLessonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "paused_seconds", nullable = false)
    private int pausedSeconds;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudySession() {
    }

    public static StudySession start(UUID userId, UUID subjectId, SessionType type, Instant now) {
        StudySession session = new StudySession();
        session.userId = userId;
        session.subjectId = subjectId;
        session.type = type;
        session.status = SessionStatus.RUNNING;
        session.startedAt = now;
        return session;
    }

    public void pause(Instant now) {
        requireStatus(SessionStatus.RUNNING);
        status = SessionStatus.PAUSED;
        pausedAt = now;
    }

    public void resume(Instant now) {
        requireStatus(SessionStatus.PAUSED);
        closePause(now);
        status = SessionStatus.RUNNING;
    }

    public void finish(Instant now) {
        end(now, SessionStatus.FINISHED);
    }

    public void abandon(Instant now) {
        end(now, SessionStatus.ABANDONED);
    }

    /** Segundos estudados até "now" (ou até o fim), descontando as pausas. */
    public long elapsedSeconds(Instant now) {
        Instant end = endedAt != null ? endedAt : now;
        long paused = pausedSeconds;
        if (pausedAt != null) {
            paused += Duration.between(pausedAt, end).toSeconds();
        }
        return Math.max(0, Duration.between(startedAt, end).toSeconds() - paused);
    }

    /** Duração efetiva em minutos (arredondada): fim - início - pausas. Só para sessões encerradas. */
    public int effectiveMinutes() {
        if (endedAt == null) {
            throw new IllegalStateException("sessão ainda em andamento");
        }
        return (int) Math.round(elapsedSeconds(endedAt) / 60.0);
    }

    private void end(Instant now, SessionStatus finalStatus) {
        if (!status.isActive()) {
            throw new IllegalStateException("sessão já encerrada");
        }
        if (status == SessionStatus.PAUSED) {
            closePause(now);
        }
        status = finalStatus;
        endedAt = now;
    }

    private void closePause(Instant now) {
        pausedSeconds += (int) Duration.between(pausedAt, now).toSeconds();
        pausedAt = null;
    }

    private void requireStatus(SessionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("sessão está " + status + ", esperado " + expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getPlannedLessonId() {
        return plannedLessonId;
    }

    public void setPlannedLessonId(UUID plannedLessonId) {
        this.plannedLessonId = plannedLessonId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public void setLessonId(UUID lessonId) {
        this.lessonId = lessonId;
    }

    public SessionType getType() {
        return type;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getPausedSeconds() {
        return pausedSeconds;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }
}
