package com.porganization.studies.dto;

import com.porganization.studies.fsrs.ReviewGrade;
import jakarta.validation.constraints.Size;

/**
 * Terminar o timer. Aula (LESSON): title obrigatório e notes opcionais, viram a Lesson.
 * Revisão (REVIEW): grade (DIFICIL, OK, FACIL) para reagendar pelo FSRS (E07).
 */
public record FinishLessonRequest(@Size(max = 200) String title, @Size(max = 5000) String notes, ReviewGrade grade) {
}
