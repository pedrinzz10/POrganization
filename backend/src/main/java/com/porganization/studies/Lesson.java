package com.porganization.studies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Uma aula estudada: o que foi visto e quanto tempo levou. Base para agendar as revisões. */
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "notes")
    private String notes;

    @Column(name = "studied_at", nullable = false)
    private Instant studiedAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Lesson() {
    }

    public Lesson(UUID userId, UUID subjectId, String title, String notes, Instant studiedAt, int durationMinutes) {
        this.userId = userId;
        this.subjectId = subjectId;
        this.title = title;
        this.notes = notes;
        this.studiedAt = studiedAt;
        this.durationMinutes = durationMinutes;
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

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getStudiedAt() {
        return studiedAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
