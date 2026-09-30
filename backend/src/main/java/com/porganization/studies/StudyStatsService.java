package com.porganization.studies;

import com.porganization.common.DateRanges;
import com.porganization.settings.UserSettingsService;
import com.porganization.studies.dto.StudyStatsResponse;
import com.porganization.studies.dto.StudyStatsResponse.LessonEntry;
import com.porganization.studies.dto.StudyStatsResponse.SubjectStats;
import com.porganization.studies.dto.StudyStatsResponse.WeekMinutes;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quanto foi estudado: por matéria, por semana e as aulas do período. Datas no fuso do usuário. */
@Service
public class StudyStatsService {

    private final StudySessionRepository sessions;
    private final SubjectRepository subjects;
    private final LessonRepository lessons;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public StudyStatsService(StudySessionRepository sessions, SubjectRepository subjects, LessonRepository lessons,
            UserSettingsService userSettings, Clock clock) {
        this.sessions = sessions;
        this.subjects = subjects;
        this.lessons = lessons;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudyStatsResponse stats(UUID userId, LocalDate from, LocalDate to) {
        DateRanges.validate(from, to);
        ZoneId zone = userSettings.zoneOf(userId);

        List<StudySession> finished = finishedBetween(userId, from, to.plusDays(1), zone);

        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate thisMonday = monday(today);
        Map<UUID, Long> lessonsThisWeek = finishedBetween(userId, thisMonday, thisMonday.plusDays(7), zone).stream()
                .filter(s -> s.getType() == SessionType.LESSON)
                .collect(Collectors.groupingBy(StudySession::getSubjectId, Collectors.counting()));

        Map<UUID, List<StudySession>> bySubject = finished.stream().collect(Collectors.groupingBy(StudySession::getSubjectId));
        List<SubjectStats> perSubject = subjects.findByUserIdOrderByPriorityOrderAsc(userId).stream()
                .filter(s -> !s.isArchived() || bySubject.containsKey(s.getId()))
                .map(s -> {
                    List<StudySession> list = bySubject.getOrDefault(s.getId(), List.of());
                    return new SubjectStats(s.getId(), s.getName(), s.getColor(), minutes(list),
                            count(list, SessionType.LESSON), count(list, SessionType.REVIEW),
                            lessonsThisWeek.getOrDefault(s.getId(), 0L), s.getSessionsPerWeek());
                })
                .toList();

        List<WeekMinutes> weeks = new ArrayList<>();
        for (LocalDate week = monday(from); !week.isAfter(to); week = week.plusWeeks(1)) {
            LocalDate start = week;
            LocalDate end = week.plusDays(7);
            weeks.add(new WeekMinutes(week, minutes(finished.stream()
                    .filter(s -> {
                        LocalDate day = LocalDate.ofInstant(s.getStartedAt(), zone);
                        return !day.isBefore(start) && day.isBefore(end);
                    }).toList())));
        }

        List<LessonEntry> lessonEntries = lessons
                .findByUserIdAndStudiedAtGreaterThanEqualAndStudiedAtLessThanOrderByStudiedAtDesc(userId,
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream()
                .map(l -> new LessonEntry(l.getId(), l.getSubjectId(), l.getTitle(), l.getNotes(), l.getStudiedAt(),
                        l.getDurationMinutes()))
                .toList();

        return new StudyStatsResponse(from, to, minutes(finished), count(finished, SessionType.REVIEW), perSubject, weeks,
                lessonEntries);
    }

    /** Sessões FINISHED que começaram de "fromDay" (inclusive) até "toDayExclusive" (exclusive). */
    private List<StudySession> finishedBetween(UUID userId, LocalDate fromDay, LocalDate toDayExclusive, ZoneId zone) {
        Instant start = fromDay.atStartOfDay(zone).toInstant();
        Instant end = toDayExclusive.atStartOfDay(zone).toInstant();
        return sessions.findByUserIdAndStatusAndStartedAtBetween(userId, SessionStatus.FINISHED, start, end).stream()
                .filter(s -> s.getStartedAt().isBefore(end))
                .toList();
    }

    private static long minutes(List<StudySession> list) {
        return list.stream().mapToLong(StudySession::effectiveMinutes).sum();
    }

    private static long count(List<StudySession> list, SessionType type) {
        return list.stream().filter(s -> s.getType() == type).count();
    }

    private static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
