package com.porganization.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Hábito que se repete (todo dia ou em dias da semana), marcado como feito dia a dia. */
@Entity
@Table(name = "daily_tasks")
public class DailyTask {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "emoji")
    private String emoji;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "created_on", nullable = false, updatable = false)
    private LocalDate createdOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DailyTask() {
    }

    public DailyTask(UUID userId, String title, String emoji, int position, LocalDate createdOn) {
        this.userId = userId;
        this.title = title;
        this.emoji = emoji;
        this.position = position;
        this.createdOn = createdOn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public LocalDate getCreatedOn() {
        return createdOn;
    }
}
