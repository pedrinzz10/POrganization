package com.porganization.studies;

import com.porganization.settings.UserSettingsService;
import com.porganization.studies.DailyStudyPlanner.DueReview;
import com.porganization.studies.DailyStudyPlanner.Plan;
import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import com.porganization.studies.dto.StudyTodayResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Busca os dados do dia e entrega ao DailyStudyPlanner. "Hoje" e a semana são no fuso do usuário. */
@Service
public class StudyTodayService {

    private final ReviewItemRepository reviewItems;
    private final LessonRepository lessons;
    private final SubjectRepository subjects;
    private final StudySessionRepository sessions;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public StudyTodayService(ReviewItemRepository reviewItems, LessonRepository lessons, SubjectRepository subjects,
            StudySessionRepository sessions, UserSettingsService userSettings, Clock clock) {
        this.reviewItems = reviewItems;
        this.lessons = lessons;
        this.subjects = subjects;
        this.sessions = sessions;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudyTodayResponse today(UUID userId) {
        ZoneId zone = userSettings.zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Plan plan = plan(userId, today, zone);
        return new StudyTodayResponse(today, plan.reviews(), plan.lessons());
    }

    @Transactional(readOnly = true)
    public Plan plan(UUID userId, LocalDate today, ZoneId zone) {
        List<Subject> active = subjects.findByUserIdOrderByPriorityOrderAsc(userId).stream()
                .filter(s -> !s.isArchived()).toList();
        Map<UUID, Subject> subjectById = active.stream().collect(Collectors.toMap(Subject::getId, Function.identity()));

        List<ReviewItem> due = reviewItems.findByUserIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(userId, today);
        Set<UUID> lessonIds = due.stream().map(ReviewItem::getLessonId).collect(Collectors.toSet());
        Map<UUID, Lesson> lessonById = lessonIds.isEmpty() ? Map.of()
                : lessons.findByUserIdAndIdIn(userId, lessonIds).stream().collect(Collectors.toMap(Lesson::getId, Function.identity()));

        List<DueReview> dueReviews = due.stream()
                .filter(i -> subjectById.containsKey(i.getSubjectId()) && lessonById.containsKey(i.getLessonId()))
                .map(i -> new DueReview(i.getLessonId(), i.getSubjectId(), subjectById.get(i.getSubjectId()).getName(),
                        lessonById.get(i.getLessonId()).getTitle(), i.getDueDate(), i.getReviewMinutes()))
                .toList();

        List<SubjectGoal> goals = active.stream()
                .map(s -> new SubjectGoal(s.getId(), s.getName(), s.getColor(), s.getPriorityOrder(), s.getSessionsPerWeek(),
                        s.getLessonMinutes()))
                .toList();

        // Semana de segunda a domingo, no fuso do usuário
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(zone).toInstant();
        Instant weekEnd = monday.plusDays(7).atStartOfDay(zone).toInstant();
        Map<UUID, Long> lessonsThisWeek = sessions
                .findByUserIdAndStatusAndStartedAtBetween(userId, SessionStatus.FINISHED, weekStart, weekEnd).stream()
                .filter(s -> s.getType() == SessionType.LESSON && s.getStartedAt().isBefore(weekEnd))
                .collect(Collectors.groupingBy(StudySession::getSubjectId, Collectors.counting()));

        return DailyStudyPlanner.plan(today, dueReviews, goals, lessonsThisWeek);
    }
}
