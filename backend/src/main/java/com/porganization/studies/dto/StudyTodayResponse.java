package com.porganization.studies.dto;

import com.porganization.studies.DailyStudyPlanner.LessonSuggestion;
import com.porganization.studies.DailyStudyPlanner.ReviewSuggestion;
import java.time.LocalDate;
import java.util.List;

/** Plano de estudo do dia: revisões vencidas primeiro, depois as aulas sugeridas. */
public record StudyTodayResponse(LocalDate date, List<ReviewSuggestion> reviews, List<LessonSuggestion> lessons) {
}
