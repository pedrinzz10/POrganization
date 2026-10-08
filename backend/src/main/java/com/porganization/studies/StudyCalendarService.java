package com.porganization.studies;

import com.porganization.commitments.recurrence.WeekDay;
import com.porganization.common.InvalidRequestException;
import com.porganization.settings.UserSettingsService;
import com.porganization.studies.DailyStudyPlanner.DueReview;
import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import com.porganization.studies.StudyCalendarPlanner.Day;
import com.porganization.studies.StudyCalendarPlanner.DoneSession;
import com.porganization.studies.StudyCalendarPlanner.PendingLesson;
import com.porganization.studies.StudyCalendarPlanner.WeekPlans;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca sessões, revisões, metas, aulas fixadas, planos da semana e aulas definidas do intervalo e
 * entrega ao StudyCalendarPlanner, no fuso do usuário. Mudar o plano fica no StudyWeekPlanService.
 */
@Service
public class StudyCalendarService {

    /** A grade do mês tem 42 dias; um pouco de folga para quem pedir intervalos maiores. */
    static final int MAX_DAYS = 62;

    /** O que a distribuição precisa saber da semana de hoje. */
    public record WeekContext(LocalDate today, List<SubjectGoal> goals, Map<UUID, Long> lessonsThisWeek, Set<UUID> lessonToday) {
    }

    private final ReviewItemRepository reviewItems;
    private final LessonRepository lessons;
    private final SubjectRepository subjects;
    private final StudySessionRepository sessions;
    private final UserSettingsService userSettings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public StudyCalendarService(ReviewItemRepository reviewItems, LessonRepository lessons, SubjectRepository subjects,
            StudySessionRepository sessions, UserSettingsService userSettings, JdbcTemplate jdbc, Clock clock) {
        this.reviewItems = reviewItems;
        this.lessons = lessons;
        this.subjects = subjects;
        this.sessions = sessions;
        this.userSettings = userSettings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(userSettings.zoneOf(userId)));
    }

    @Transactional(readOnly = true)
    public List<Day> calendar(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("to", "o fim não pode ser antes do início");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidRequestException("to", "consulte no máximo " + MAX_DAYS + " dias de uma vez");
        }
        ZoneId zone = userSettings.zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        // Consulta no futuro: calcula desde hoje, para as aulas definidas seguirem a sequência da semana de hoje
        LocalDate start = from.isAfter(today) ? today : from;

        List<Subject> all = subjects.findByUserIdOrderByPriorityOrderAsc(userId);
        Map<UUID, Subject> subjectById = all.stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
        List<SubjectGoal> goals = goals(all);

        // Sessões concluídas do intervalo e da semana de hoje (a meta desconta o que já foi feito)
        LocalDate monday = mondayOf(today);
        LocalDate sessionsFrom = start.isBefore(monday) ? start : monday;
        LocalDate sessionsTo = to.isAfter(today) ? to : today;
        List<StudySession> finished = sessions.findByUserIdAndStatusAndStartedAtBetween(userId, SessionStatus.FINISHED,
                startOf(sessionsFrom, zone), startOf(sessionsTo.plusDays(1), zone));

        List<ReviewItem> due = reviewItems.findByUserIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(userId, to);

        Set<UUID> lessonIds = Stream.concat(finished.stream().map(StudySession::getLessonId), due.stream().map(ReviewItem::getLessonId))
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        Map<UUID, Lesson> lessonById = lessonIds.isEmpty() ? Map.of()
                : lessons.findByUserIdAndIdIn(userId, lessonIds).stream().collect(Collectors.toMap(Lesson::getId, Function.identity()));

        List<DoneSession> done = finished.stream()
                .filter(s -> subjectById.containsKey(s.getSubjectId()))
                .map(s -> {
                    Subject subject = subjectById.get(s.getSubjectId());
                    Lesson lesson = s.getLessonId() == null ? null : lessonById.get(s.getLessonId());
                    return new DoneSession(LocalDate.ofInstant(s.getStartedAt(), zone), subject.getId(), subject.getName(),
                            subject.getColor(), s.getType(), lesson == null ? null : lesson.getTitle(), s.effectiveMinutes());
                })
                .toList();

        List<DueReview> reviews = due.stream()
                .filter(i -> subjectById.containsKey(i.getSubjectId()) && !subjectById.get(i.getSubjectId()).isArchived()
                        && lessonById.containsKey(i.getLessonId()))
                .map(i -> new DueReview(i.getLessonId(), i.getSubjectId(), subjectById.get(i.getSubjectId()).getName(),
                        lessonById.get(i.getLessonId()).getTitle(), i.getDueDate(), i.getReviewMinutes()))
                .toList();

        Map<UUID, Long> lessonsThisWeek = lessonsThisWeek(done, today);
        Set<UUID> lessonToday = lessonToday(done, today);

        // Fixadas e planos da semana de "start" até a de "to" (a distribuição olha a semana inteira)
        LocalDate firstMonday = mondayOf(start);
        LocalDate lastSunday = mondayOf(to).plusDays(6);
        Map<UUID, List<LocalDate>> pins = new HashMap<>();
        jdbc.query("select subject_id, day from study_lesson_pins where user_id = ? and day between ? and ?",
                rs -> {
                    pins.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getObject(2, LocalDate.class));
                }, userId, Date.valueOf(firstMonday), Date.valueOf(lastSunday));

        List<Day> days = StudyCalendarPlanner.plan(today, start, to, done, reviews, goals, lessonsThisWeek, lessonToday, pins,
                weekPlans(userId, firstMonday, lastSunday), pendingLessons(userId));
        return days.stream().filter(d -> !d.date().isBefore(from)).toList();
    }

    /** Hoje, metas ativas e aulas já feitas na semana de hoje (para gerar a semana). */
    @Transactional(readOnly = true)
    public WeekContext weekContext(UUID userId) {
        ZoneId zone = userSettings.zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Subject> all = subjects.findByUserIdOrderByPriorityOrderAsc(userId);
        Map<UUID, Subject> subjectById = all.stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
        List<DoneSession> done = sessions.findByUserIdAndStatusAndStartedAtBetween(userId, SessionStatus.FINISHED,
                        startOf(mondayOf(today), zone), startOf(today.plusDays(1), zone)).stream()
                .filter(s -> subjectById.containsKey(s.getSubjectId()))
                .map(s -> new DoneSession(LocalDate.ofInstant(s.getStartedAt(), zone), s.getSubjectId(), null, null, s.getType(),
                        null, 0))
                .toList();
        return new WeekContext(today, goals(all), lessonsThisWeek(done, today), lessonToday(done, today));
    }

    private static List<SubjectGoal> goals(List<Subject> all) {
        return all.stream()
                .filter(s -> !s.isArchived())
                .map(s -> new SubjectGoal(s.getId(), s.getName(), s.getColor(), s.getPriorityOrder(), s.getSessionsPerWeek(),
                        s.getLessonMinutes(), s.getStudyDays().stream().map(WeekDay::toDayOfWeek).collect(Collectors.toSet())))
                .toList();
    }

    private static Map<UUID, Long> lessonsThisWeek(List<DoneSession> done, LocalDate today) {
        LocalDate monday = mondayOf(today);
        return done.stream()
                .filter(s -> s.type() == SessionType.LESSON && !s.date().isBefore(monday) && !s.date().isAfter(today))
                .collect(Collectors.groupingBy(DoneSession::subjectId, Collectors.counting()));
    }

    private static Set<UUID> lessonToday(List<DoneSession> done, LocalDate today) {
        return done.stream()
                .filter(s -> s.type() == SessionType.LESSON && s.date().equals(today))
                .map(DoneSession::subjectId)
                .collect(Collectors.toSet());
    }

    private WeekPlans weekPlans(UUID userId, LocalDate firstMonday, LocalDate lastSunday) {
        Set<LocalDate> weeks = new HashSet<>(jdbc.queryForList(
                "select week from study_week_plans where user_id = ? and week between ? and ?", LocalDate.class,
                userId, Date.valueOf(firstMonday), Date.valueOf(lastSunday)));
        if (weeks.isEmpty()) {
            return WeekPlans.NONE;
        }
        Map<LocalDate, List<UUID>> slots = new LinkedHashMap<>();
        jdbc.query("select day, subject_id from study_week_slots where user_id = ? and day between ? and ? order by day, created_at, id",
                rs -> {
                    slots.computeIfAbsent(rs.getObject(1, LocalDate.class), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
                }, userId, Date.valueOf(firstMonday), Date.valueOf(lastSunday));
        return new WeekPlans(weeks, slots);
    }

    /** Aulas definidas ainda não estudadas das matérias PLANNED, na ordem do curso. */
    private Map<UUID, List<PendingLesson>> pendingLessons(UUID userId) {
        Map<UUID, List<PendingLesson>> pending = new HashMap<>();
        jdbc.query("""
                select p.subject_id, p.id, p.title
                from planned_lessons p join subjects s on s.id = p.subject_id
                where p.user_id = ? and p.lesson_id is null and s.lesson_mode = 'PLANNED'
                order by p.subject_id, p.position
                """, rs -> {
            pending.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(new PendingLesson(rs.getObject(2, UUID.class), rs.getString(3)));
        }, userId);
        return pending;
    }

    static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Instant startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }
}
