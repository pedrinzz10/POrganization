package com.porganization.studies;

import com.porganization.studies.fsrs.FsrsCard;
import com.porganization.studies.fsrs.ReviewGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Revisão (mini aula) de uma aula, agendada pelo FSRS. Guarda o estado de memória do item. */
@Entity
@Table(name = "review_items")
public class ReviewItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fsrs_state", nullable = false)
    private FsrsCard.State fsrsState;

    @Column(name = "stability", nullable = false)
    private double stability;

    @Column(name = "difficulty", nullable = false)
    private double difficulty;

    @Column(name = "reps", nullable = false)
    private int reps;

    @Column(name = "lapses", nullable = false)
    private int lapses;

    @Column(name = "last_review")
    private LocalDate lastReview;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "review_minutes", nullable = false)
    private int reviewMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_grade")
    private ReviewGrade lastGrade;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReviewItem() {
    }

    public ReviewItem(UUID userId, UUID lessonId, UUID subjectId, LocalDate dueDate, int reviewMinutes) {
        this.userId = userId;
        this.lessonId = lessonId;
        this.subjectId = subjectId;
        this.fsrsState = FsrsCard.State.NEW;
        this.dueDate = dueDate;
        this.reviewMinutes = reviewMinutes;
    }

    public FsrsCard toCard() {
        return new FsrsCard(fsrsState, stability, difficulty, reps, lapses, lastReview, dueDate, 0);
    }

    /** Guarda o resultado do FSRS depois de uma revisão com a nota dada. */
    public void apply(FsrsCard card, ReviewGrade grade) {
        this.fsrsState = card.state();
        this.stability = card.stability();
        this.difficulty = card.difficulty();
        this.reps = card.reps();
        this.lapses = card.lapses();
        this.lastReview = card.lastReview();
        this.dueDate = card.due();
        this.lastGrade = grade;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public FsrsCard.State getFsrsState() {
        return fsrsState;
    }

    public int getReps() {
        return reps;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getReviewMinutes() {
        return reviewMinutes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public ReviewGrade getLastGrade() {
        return lastGrade;
    }
}
