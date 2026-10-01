package com.porganization.studies;

import com.porganization.commitments.recurrence.WeekDay;
import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.settings.UserSettingsService;
import com.porganization.studies.DailyStudyPlanner.DueReview;
import com.porganization.studies.DailyStudyPlanner.SubjectGoal;
import com.porganization.studies.StudyCalendarPlanner.Day;
import com.porganization.studies.StudyCalendarPlanner.DoneSession;
import com.porganization.studies.StudyCalendarPlanner.Kind;
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
 * Busca sessões, revisões, metas e aulas fixadas do intervalo e entrega ao StudyCalendarPlanner, no
 * fuso do usuário. Também move uma aula de dia (arrastar na agenda) e devolve a semana ao automático.
 */
@Service
public class StudyCalendarService {

    /** A grade do mês tem 42 dias; um pouco de folga para quem pedir intervalos maiores. */
    static final int MAX_DAYS = 62;

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

        List<Subject> all = subjects.findByUserIdOrderByPriorityOrderAsc(userId);
        Map<UUID, Subject> subjectById = all.stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
        List<SubjectGoal> goals = all.stream()
                .filter(s -> !s.isArchived())
                .map(s -> new SubjectGoal(s.getId(), s.getName(), s.getColor(), s.getPriorityOrder(), s.getSessionsPerWeek(),
                        s.getLessonMinutes(), s.getStudyDays().stream().map(WeekDay::toDayOfWeek).collect(Collectors.toSet())))
                .toList();

        // Sessões concluídas do intervalo e da semana de hoje (a meta desconta o que já foi feito)
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sessionsFrom = from.isBefore(monday) ? from : monday;
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

        Map<UUID, Long> lessonsThisWeek = done.stream()
                .filter(s -> s.type() == SessionType.LESSON && !s.date().isBefore(monday) && !s.date().isAfter(today))
                .collect(Collectors.groupingBy(DoneSession::subjectId, Collectors.counting()));
        Set<UUID> lessonToday = done.stream()
                .filter(s -> s.type() == SessionType.LESSON && s.date().equals(today))
                .map(DoneSession::subjectId)
                .collect(Collectors.toSet());

        // Fixadas da semana de "from" até a de "to" (a distribuição olha a semana inteira)
        Map<UUID, List<LocalDate>> pins = new HashMap<>();
        jdbc.query("select subject_id, day from study_lesson_pins where user_id = ? and day between ? and ?",
                rs -> {
                    pins.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getObject(2, LocalDate.class));
                }, userId, Date.valueOf(mondayOf(from)), Date.valueOf(mondayOf(to).plusDays(6)));

        return StudyCalendarPlanner.plan(today, from, to, done, reviews, goals, lessonsThisWeek, lessonToday, pins);
    }

    /**
     * Move a aula da matéria de um dia para outro na mesma semana. As outras aulas dela na semana
     * ficam fixadas onde estão (senão a redistribuição poderia devolver uma aula ao dia de origem).
     * Devolve a semana atualizada.
     */
    @Transactional
    public List<Day> move(UUID userId, UUID subjectId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(userSettings.zoneOf(userId)));
        if (from.isBefore(today) || to.isBefore(today)) {
            throw new InvalidRequestException("to", "só dá para mover aulas de hoje em diante");
        }
        LocalDate monday = mondayOf(from);
        if (!mondayOf(to).equals(monday)) {
            throw new InvalidRequestException("to", "mova a aula dentro da mesma semana (a meta é semanal)");
        }
        Subject subject = subjects.findByIdAndUserId(subjectId, userId)
                .filter(s -> !s.isArchived())
                .orElseThrow(() -> new NotFoundException("Matéria não encontrada"));
        LocalDate sunday = monday.plusDays(6);
        List<LocalDate> lessonDays = calendar(userId, monday, sunday).stream()
                .filter(d -> d.items().stream().anyMatch(i -> i.kind() == Kind.LESSON && i.subjectId().equals(subject.getId())))
                .map(Day::date)
                .toList();
        if (!lessonDays.contains(from)) {
            throw new ConflictException("Essa aula não está mais nesse dia. Atualize a agenda.");
        }
        if (!from.equals(to) && lessonDays.contains(to)) {
            throw new ConflictException("Já tem aula de " + subject.getName() + " nesse dia.");
        }
        jdbc.update("delete from study_lesson_pins where user_id = ? and subject_id = ? and day between ? and ?",
                userId, subjectId, Date.valueOf(monday), Date.valueOf(sunday));
        for (LocalDate day : lessonDays) {
            LocalDate target = day.equals(from) ? to : day;
            if (!target.isBefore(today)) {
                jdbc.update("insert into study_lesson_pins (user_id, subject_id, day) values (?, ?, ?)",
                        userId, subjectId, Date.valueOf(target));
            }
        }
        return calendar(userId, monday, sunday);
    }

    /** Devolve as aulas da matéria naquela semana à distribuição automática. */
    @Transactional
    public void unpin(UUID userId, UUID subjectId, LocalDate anyDayOfWeek) {
        LocalDate monday = mondayOf(anyDayOfWeek);
        jdbc.update("delete from study_lesson_pins where user_id = ? and subject_id = ? and day between ? and ?",
                userId, subjectId, Date.valueOf(monday), Date.valueOf(monday.plusDays(6)));
    }

    private static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Instant startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }
}
