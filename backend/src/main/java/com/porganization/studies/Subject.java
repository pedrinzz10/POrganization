package com.porganization.studies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Matéria de estudo, como "Java Advanced". priorityOrder 1 é a mais importante. */
@Entity
@Table(name = "subjects")
public class Subject {

    public static final int DEFAULT_SESSIONS_PER_WEEK = 2;
    public static final int DEFAULT_LESSON_MINUTES = 50;

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "color")
    private String color;

    @Column(name = "priority_order", nullable = false)
    private int priorityOrder;

    @Column(name = "sessions_per_week", nullable = false)
    private int sessionsPerWeek = DEFAULT_SESSIONS_PER_WEEK;

    @Column(name = "lesson_minutes", nullable = false)
    private int lessonMinutes = DEFAULT_LESSON_MINUTES;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @ManyToMany
    @JoinTable(name = "subject_tags", joinColumns = @JoinColumn(name = "subject_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Subject() {
    }

    public Subject(UUID userId, String name, int priorityOrder) {
        this.userId = userId;
        this.name = name;
        this.priorityOrder = priorityOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getPriorityOrder() {
        return priorityOrder;
    }

    public void setPriorityOrder(int priorityOrder) {
        this.priorityOrder = priorityOrder;
    }

    public int getSessionsPerWeek() {
        return sessionsPerWeek;
    }

    public void setSessionsPerWeek(int sessionsPerWeek) {
        this.sessionsPerWeek = sessionsPerWeek;
    }

    public int getLessonMinutes() {
        return lessonMinutes;
    }

    public void setLessonMinutes(int lessonMinutes) {
        this.lessonMinutes = lessonMinutes;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
