package com.porganization.studies;

import com.porganization.common.NotFoundException;
import com.porganization.studies.fsrs.Fsrs;
import com.porganization.studies.fsrs.FsrsParameters;
import com.porganization.studies.fsrs.ReviewGrade;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Agenda as revisões: cada aula gera uma revisão para o dia seguinte, com metade do tempo; cada
 * revisão feita é reagendada pelo FSRS de acordo com a nota (difícil, ok, fácil).
 */
@Component
public class ReviewScheduler {

    private final ReviewItemRepository items;
    private final Fsrs fsrs = new Fsrs(FsrsParameters.defaults());

    public ReviewScheduler(ReviewItemRepository items) {
        this.items = items;
    }

    /** Aula terminada em "today" (no fuso do usuário): primeira revisão amanhã. */
    public ReviewItem onLessonFinished(Lesson lesson, LocalDate today) {
        return items.save(new ReviewItem(lesson.getUserId(), lesson.getId(), lesson.getSubjectId(), today.plusDays(1),
                reviewMinutes(lesson.getDurationMinutes())));
    }

    /** Revisão feita em "today" com a nota dada: o FSRS calcula o novo estado e o próximo dia. */
    public ReviewItem onReviewFinished(UUID userId, UUID lessonId, ReviewGrade grade, LocalDate today) {
        ReviewItem item = find(userId, lessonId);
        item.apply(fsrs.review(item.toCard(), grade, today), grade);
        return item;
    }

    public ReviewItem find(UUID userId, UUID lessonId) {
        return items.findByUserIdAndLessonId(userId, lessonId)
                .orElseThrow(() -> new NotFoundException("Revisão não encontrada para esta aula"));
    }

    /** Revisão é uma mini aula: metade do tempo da aula, arredondado para cima, no mínimo 5 minutos. */
    public static int reviewMinutes(int lessonMinutes) {
        return Math.max(5, (lessonMinutes + 1) / 2);
    }
}
