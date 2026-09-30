package com.porganization.commitments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Ajuste de uma ocorrência de um compromisso recorrente (tabela commitment_exceptions).
 * Não se chama "CommitmentException" para não ser confundida com uma exceção Java.
 */
@Entity
@Table(name = "commitment_exceptions")
public class OccurrenceOverride {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "commitment_id", nullable = false, updatable = false)
    private UUID commitmentId;

    @Column(name = "occurrence_date", nullable = false, updatable = false)
    private LocalDate occurrenceDate;

    @Column(name = "done", nullable = false)
    private boolean done;

    @Column(name = "cancelled", nullable = false)
    private boolean cancelled;

    @Column(name = "override_title")
    private String overrideTitle;

    @Column(name = "override_time")
    private LocalTime overrideTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OccurrenceOverride() {
    }

    public OccurrenceOverride(UUID userId, UUID commitmentId, LocalDate occurrenceDate) {
        this.userId = userId;
        this.commitmentId = commitmentId;
        this.occurrenceDate = occurrenceDate;
    }

    public UUID getCommitmentId() {
        return commitmentId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getOverrideTitle() {
        return overrideTitle;
    }

    public void setOverrideTitle(String overrideTitle) {
        this.overrideTitle = overrideTitle;
    }

    public LocalTime getOverrideTime() {
        return overrideTime;
    }

    public void setOverrideTime(LocalTime overrideTime) {
        this.overrideTime = overrideTime;
    }
}
