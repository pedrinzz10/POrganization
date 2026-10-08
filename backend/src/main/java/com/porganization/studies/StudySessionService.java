package com.porganization.studies;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.studies.PlannedLessonService.PlannedLesson;
import com.porganization.studies.dto.FinishLessonRequest;
import com.porganization.studies.dto.SessionResponse;
import com.porganization.studies.dto.StartSessionRequest;
import com.porganization.settings.UserSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Timer de estudo: iniciar, pausar, retomar, terminar e abandonar. "Agora" vem do Clock. */
@Service
public class StudySessionService {

    private final StudySessionRepository sessions;
    private final SubjectRepository subjects;
    private final PlannedLessonService plannedLessons;
    private final LessonRepository lessons;
    private final ReviewScheduler reviewScheduler;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public StudySessionService(StudySessionRepository sessions, SubjectRepository subjects, LessonRepository lessons,
            ReviewScheduler reviewScheduler, UserSettingsService userSettings, Clock clock, PlannedLessonService plannedLessons) {
        this.sessions = sessions;
        this.subjects = subjects;
        this.plannedLessons = plannedLessons;
        this.lessons = lessons;
        this.reviewScheduler = reviewScheduler;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    /** Só uma sessão em andamento por usuário: outra ativa responde 409. */
    @Transactional
    public SessionResponse start(UUID userId, StartSessionRequest request) {
        Subject subject = subjects.findByIdAndUserId(request.subjectId(), userId)
                .orElseThrow(() -> new NotFoundException("Matéria não encontrada"));
        if (sessions.findActive(userId).isPresent()) {
            throw new ConflictException("Já existe uma sessão de estudo em andamento. Termine ou abandone antes de começar outra.");
        }
        StudySession session = StudySession.start(userId, subject.getId(), request.type(), clock.instant());
        if (request.type() == SessionType.LESSON && subject.getLessonMode() == LessonMode.PLANNED) {
            session.setPlannedLessonId(plannedLessonFor(userId, subject, request.plannedLessonId()));
        }
        if (request.type() == SessionType.REVIEW) {
            if (request.lessonId() == null) {
                throw new InvalidRequestException("lessonId", "informe a aula que será revisada");
            }
            Lesson lesson = lessons.findByIdAndUserId(request.lessonId(), userId)
                    .filter(l -> l.getSubjectId().equals(subject.getId()))
                    .orElseThrow(() -> new NotFoundException("Aula não encontrada"));
            session.setLessonId(lesson.getId());
        }
        try {
            // flush aqui: se outra requisição criou uma sessão ao mesmo tempo, o índice único barra
            return toResponse(sessions.saveAndFlush(session), subject);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe uma sessão de estudo em andamento.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<SessionResponse> active(UUID userId) {
        return sessions.findActive(userId).map(s -> toResponse(s, subjectOf(s)));
    }

    @Transactional
    public SessionResponse pause(UUID userId, UUID id) {
        return transition(userId, id, StudySession::pause);
    }

    @Transactional
    public SessionResponse resume(UUID userId, UUID id) {
        return transition(userId, id, StudySession::resume);
    }

    @Transactional
    public SessionResponse abandon(UUID userId, UUID id) {
        return transition(userId, id, StudySession::abandon);
    }

    /**
     * Aula: cria a Lesson com a duração efetiva e agenda a primeira revisão para amanhã.
     * Revisão: exige a nota e reagenda pelo FSRS. "Hoje" é no fuso do usuário.
     */
    @Transactional
    public SessionResponse finish(UUID userId, UUID id, FinishLessonRequest request) {
        StudySession session = find(userId, id);
        String title = request.title();
        if (session.getType() == SessionType.LESSON && (title == null || title.isBlank()) && session.getPlannedLessonId() != null) {
            // Aula da lista: sem título, vale o nome dela
            title = plannedLessons.find(userId, session.getSubjectId(), session.getPlannedLessonId())
                    .map(PlannedLesson::title).orElse(null);
        }
        if (session.getType() == SessionType.LESSON && (title == null || title.isBlank())) {
            throw new InvalidRequestException("title", "dê um título para a aula");
        }
        if (session.getType() == SessionType.REVIEW && request.grade() == null) {
            throw new InvalidRequestException("grade", "escolha como foi a revisão: DIFICIL, OK ou FACIL");
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, userSettings.zoneOf(userId));
        apply(session, now, StudySession::finish);
        if (session.getType() == SessionType.LESSON) {
            // flush: a ligação com a aula definida é feita por SQL e precisa da aula já gravada
            Lesson lesson = lessons.saveAndFlush(new Lesson(userId, session.getSubjectId(), title.trim(),
                    blankToNull(request.notes()), now, session.effectiveMinutes()));
            session.setLessonId(lesson.getId());
            if (session.getPlannedLessonId() != null) {
                plannedLessons.markStudied(session.getPlannedLessonId(), lesson.getId());
            }
            reviewScheduler.onLessonFinished(lesson, today);
        } else {
            reviewScheduler.onReviewFinished(userId, session.getLessonId(), request.grade(), today);
        }
        return toResponse(session, subjectOf(session));
    }

    private SessionResponse transition(UUID userId, UUID id, BiConsumer<StudySession, Instant> action) {
        StudySession session = find(userId, id);
        apply(session, clock.instant(), action);
        return toResponse(session, subjectOf(session));
    }

    private static void apply(StudySession session, Instant now, BiConsumer<StudySession, Instant> action) {
        try {
            action.accept(session, now);
        } catch (IllegalStateException e) {
            throw new ConflictException("A sessão não permite essa ação agora (" + session.getStatus() + ").");
        }
    }

    private StudySession find(UUID userId, UUID id) {
        return sessions.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Sessão não encontrada"));
    }

    private Subject subjectOf(StudySession session) {
        return subjects.findByIdAndUserId(session.getSubjectId(), session.getUserId()).orElseThrow();
    }

    private SessionResponse toResponse(StudySession s, Subject subject) {
        String plannedTitle = s.getPlannedLessonId() == null ? null
                : plannedLessons.find(s.getUserId(), s.getSubjectId(), s.getPlannedLessonId()).map(PlannedLesson::title).orElse(null);
        return new SessionResponse(s.getId(), s.getSubjectId(), subject.getName(), s.getLessonId(), s.getType(),
                s.getStatus(), s.getStartedAt(), s.getEndedAt(), s.getPausedSeconds(), s.getPausedAt(),
                s.elapsedSeconds(clock.instant()), plannedMinutes(s, subject), s.getPlannedLessonId(), plannedTitle);
    }

    /**
     * A aula da lista que a sessão vai estudar: a escolhida (precisa ser da matéria e não estudada)
     * ou a próxima pendente. Lista vazia ou toda estudada: null (o título é digitado, como na livre).
     */
    private UUID plannedLessonFor(UUID userId, Subject subject, UUID chosen) {
        if (chosen == null) {
            return plannedLessons.firstPending(userId, subject.getId()).map(PlannedLesson::id).orElse(null);
        }
        PlannedLesson lesson = plannedLessons.find(userId, subject.getId(), chosen)
                .orElseThrow(() -> new NotFoundException("Aula não encontrada"));
        if (lesson.lessonId() != null) {
            throw new ConflictException("Essa aula já foi estudada.");
        }
        return lesson.id();
    }

    /** Tempo sugerido no timer: a duração da aula da matéria; para revisão, o tempo agendado da revisão. */
    private int plannedMinutes(StudySession session, Subject subject) {
        if (session.getType() == SessionType.REVIEW && session.getLessonId() != null) {
            return reviewScheduler.find(session.getUserId(), session.getLessonId()).getReviewMinutes();
        }
        return subject.getLessonMinutes();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
