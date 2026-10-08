package com.porganization.studies;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.studies.dto.SubjectResponse.PlannedCount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aulas definidas de uma matéria (E14): a lista na ordem do curso. position vai de 1 a N sem
 * buracos; incluir põe no fim, excluir e reordenar renumeram.
 */
@Service
public class PlannedLessonService {

    /** Uma aula da lista; lessonId e studiedAt preenchidos quando já foi estudada. */
    public record PlannedLesson(UUID id, String title, int position, UUID lessonId, Instant studiedAt) {
    }

    static final int MAX_LESSONS = 500;

    private final JdbcTemplate jdbc;
    private final SubjectRepository subjects;

    public PlannedLessonService(JdbcTemplate jdbc, SubjectRepository subjects) {
        this.jdbc = jdbc;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public List<PlannedLesson> list(UUID userId, UUID subjectId) {
        requireSubject(userId, subjectId);
        return query(userId, subjectId);
    }

    /** Inclui as aulas no fim da lista, na ordem recebida (títulos em branco são ignorados). */
    @Transactional
    public List<PlannedLesson> add(UUID userId, UUID subjectId, List<String> titles) {
        requireSubject(userId, subjectId);
        List<String> clean = titles.stream().map(String::strip).filter(t -> !t.isEmpty()).toList();
        if (clean.isEmpty()) {
            throw new InvalidRequestException("titles", "informe pelo menos uma aula");
        }
        if (clean.stream().anyMatch(t -> t.length() > 200)) {
            throw new InvalidRequestException("titles", "cada aula pode ter até 200 caracteres");
        }
        int last = jdbc.queryForObject("select coalesce(max(position), 0) from planned_lessons where subject_id = ?",
                Integer.class, subjectId);
        if (last + clean.size() > MAX_LESSONS) {
            throw new InvalidRequestException("titles", "uma matéria pode ter até " + MAX_LESSONS + " aulas");
        }
        for (String title : clean) {
            jdbc.update("insert into planned_lessons (user_id, subject_id, title, position) values (?, ?, ?, ?)",
                    userId, subjectId, title, ++last);
        }
        return query(userId, subjectId);
    }

    @Transactional
    public PlannedLesson rename(UUID userId, UUID subjectId, UUID id, String title) {
        requireSubject(userId, subjectId);
        if (jdbc.update("update planned_lessons set title = ? where id = ? and subject_id = ? and user_id = ?",
                title.strip(), id, subjectId, userId) == 0) {
            throw new NotFoundException("Aula não encontrada");
        }
        return query(userId, subjectId).stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void delete(UUID userId, UUID subjectId, UUID id) {
        requireSubject(userId, subjectId);
        if (jdbc.update("delete from planned_lessons where id = ? and subject_id = ? and user_id = ?", id, subjectId, userId) == 0) {
            throw new NotFoundException("Aula não encontrada");
        }
        renumber(query(userId, subjectId).stream().map(PlannedLesson::id).toList());
    }

    /** Grava a sequência na ordem dos ids; a lista precisa ter todas as aulas da matéria, sem repetir. */
    @Transactional
    public List<PlannedLesson> reorder(UUID userId, UUID subjectId, List<UUID> ids) {
        requireSubject(userId, subjectId);
        List<UUID> current = query(userId, subjectId).stream().map(PlannedLesson::id).toList();
        if (new HashSet<>(ids).size() != ids.size() || ids.size() != current.size() || !current.containsAll(ids)) {
            throw new InvalidRequestException("ids", "envie exatamente as aulas da matéria, sem faltar nem repetir");
        }
        renumber(ids);
        return query(userId, subjectId);
    }

    /** Total e estudadas por matéria do usuário. */
    @Transactional(readOnly = true)
    public Map<UUID, PlannedCount> counts(UUID userId) {
        Map<UUID, PlannedCount> counts = new HashMap<>();
        jdbc.query("select subject_id, count(*), count(lesson_id) from planned_lessons where user_id = ? group by subject_id",
                rs -> {
                    counts.put(rs.getObject(1, UUID.class), new PlannedCount(rs.getInt(2), rs.getInt(3)));
                }, userId);
        return counts;
    }

    /** A próxima aula não estudada da matéria, na ordem do curso. */
    @Transactional(readOnly = true)
    public Optional<PlannedLesson> firstPending(UUID userId, UUID subjectId) {
        return query(userId, subjectId).stream().filter(l -> l.lessonId() == null).findFirst();
    }

    /** A próxima aula não estudada de cada matéria com aulas definidas. */
    @Transactional(readOnly = true)
    public Map<UUID, PlannedLesson> firstPendingBySubject(UUID userId) {
        Map<UUID, PlannedLesson> next = new HashMap<>();
        jdbc.query("""
                select distinct on (p.subject_id) p.subject_id, p.id, p.title, p.position
                from planned_lessons p join subjects s on s.id = p.subject_id
                where p.user_id = ? and p.lesson_id is null and s.lesson_mode = 'PLANNED'
                order by p.subject_id, p.position
                """, rs -> {
            next.put(rs.getObject(1, UUID.class),
                    new PlannedLesson(rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4), null, null));
        }, userId);
        return next;
    }

    @Transactional(readOnly = true)
    public Optional<PlannedLesson> find(UUID userId, UUID subjectId, UUID id) {
        return query(userId, subjectId).stream().filter(l -> l.id().equals(id)).findFirst();
    }

    /** Liga a aula definida à aula estudada (só se ainda estava pendente). */
    @Transactional
    public void markStudied(UUID id, UUID lessonId) {
        jdbc.update("update planned_lessons set lesson_id = ? where id = ? and lesson_id is null", lessonId, id);
    }

    private void renumber(List<UUID> ids) {
        int position = 1;
        for (UUID id : ids) {
            jdbc.update("update planned_lessons set position = ? where id = ?", position++, id);
        }
    }

    private List<PlannedLesson> query(UUID userId, UUID subjectId) {
        return jdbc.query("""
                select p.id, p.title, p.position, p.lesson_id, l.studied_at
                from planned_lessons p left join lessons l on l.id = p.lesson_id
                where p.user_id = ? and p.subject_id = ?
                order by p.position
                """, (rs, n) -> {
            Timestamp studiedAt = rs.getTimestamp(5);
            return new PlannedLesson(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getObject(4, UUID.class),
                    studiedAt == null ? null : studiedAt.toInstant());
        }, userId, subjectId);
    }

    private void requireSubject(UUID userId, UUID subjectId) {
        subjects.findByIdAndUserId(subjectId, userId).orElseThrow(() -> new NotFoundException("Matéria não encontrada"));
    }
}
