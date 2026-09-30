package com.porganization.studies;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.studies.dto.FinishLessonRequest;
import com.porganization.studies.dto.SessionResponse;
import com.porganization.studies.dto.StartSessionRequest;
import java.time.Clock;
import java.time.Instant;
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
    private final LessonRepository lessons;
    private final Clock clock;

    public StudySessionService(StudySessionRepository sessions, SubjectRepository subjects, LessonRepository lessons,
            Clock clock) {
        this.sessions = sessions;
        this.subjects = subjects;
        this.lessons = lessons;
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

    /** Aula: cria a Lesson com a duração efetiva e liga à sessão. Revisão: ver E07. */
    @Transactional
    public SessionResponse finish(UUID userId, UUID id, FinishLessonRequest request) {
        StudySession session = find(userId, id);
        if (session.getType() == SessionType.LESSON && (request.title() == null || request.title().isBlank())) {
            throw new InvalidRequestException("title", "dê um título para a aula");
        }
        Instant now = clock.instant();
        apply(session, now, StudySession::finish);
        if (session.getType() == SessionType.LESSON) {
            Lesson lesson = lessons.save(new Lesson(userId, session.getSubjectId(), request.title().trim(),
                    blankToNull(request.notes()), now, session.effectiveMinutes()));
            session.setLessonId(lesson.getId());
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
        return new SessionResponse(s.getId(), s.getSubjectId(), subject.getName(), s.getLessonId(), s.getType(),
                s.getStatus(), s.getStartedAt(), s.getEndedAt(), s.getPausedSeconds(), s.getPausedAt(),
                s.elapsedSeconds(clock.instant()), plannedMinutes(s, subject));
    }

    /** Tempo sugerido no timer: a duração da aula da matéria; para revisão, metade da aula revisada. */
    private int plannedMinutes(StudySession session, Subject subject) {
        if (session.getType() == SessionType.REVIEW && session.getLessonId() != null) {
            return lessons.findByIdAndUserId(session.getLessonId(), session.getUserId())
                    .map(l -> reviewMinutes(l.getDurationMinutes()))
                    .orElse(subject.getLessonMinutes());
        }
        return subject.getLessonMinutes();
    }

    /** Revisão é uma mini aula: metade do tempo da aula, arredondado para cima, no mínimo 5 minutos. */
    static int reviewMinutes(int lessonMinutes) {
        return Math.max(5, (lessonMinutes + 1) / 2);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
