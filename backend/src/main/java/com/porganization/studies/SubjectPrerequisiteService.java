package com.porganization.studies;

import com.porganization.common.InvalidRequestException;
import com.porganization.studies.dto.SubjectResponse.PlannedCount;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pré-requisitos entre matérias (E17). Uma matéria está completa quando todas as aulas definidas
 * dela foram estudadas, ou quando foi marcada como concluída (a livre só termina assim). Ela fica
 * bloqueada enquanto alguma de que depende (não arquivada) não estiver completa. Bloqueadas e
 * concluídas ficam "em espera": fora do gerador, da previsão automática e das sugestões de Hoje,
 * mas ainda dá para incluir à mão no plano e estudar.
 */
@Service
public class SubjectPrerequisiteService {

    /** Matéria de que outra depende e ainda não terminou, com o progresso das aulas definidas. */
    public record Blocker(UUID id, String name, int done, int total) {
    }

    /** Estado de uma matéria: completa e o que ainda a bloqueia. */
    public record Status(boolean completed, List<UUID> prerequisiteIds, List<Blocker> blockedBy) {

        public boolean onHold() {
            return completed || !blockedBy.isEmpty();
        }
    }

    private final JdbcTemplate jdbc;
    private final SubjectRepository subjects;
    private final PlannedLessonService plannedLessons;

    public SubjectPrerequisiteService(JdbcTemplate jdbc, SubjectRepository subjects, PlannedLessonService plannedLessons) {
        this.jdbc = jdbc;
        this.subjects = subjects;
        this.plannedLessons = plannedLessons;
    }

    /** Estado de cada matéria do usuário. */
    @Transactional(readOnly = true)
    public Map<UUID, Status> statuses(UUID userId) {
        List<Subject> all = subjects.findByUserIdOrderByPriorityOrderAsc(userId);
        Map<UUID, Subject> byId = all.stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
        Map<UUID, PlannedCount> counts = plannedLessons.counts(userId);
        Map<UUID, List<UUID>> requires = prerequisites(userId);

        Map<UUID, Status> statuses = new HashMap<>();
        for (Subject s : all) {
            List<UUID> required = requires.getOrDefault(s.getId(), List.of());
            List<Blocker> blockedBy = required.stream()
                    .map(byId::get)
                    .filter(r -> r != null && !r.isArchived() && !completed(r, counts))
                    .map(r -> {
                        PlannedCount c = counts.getOrDefault(r.getId(), PlannedCount.NONE);
                        return new Blocker(r.getId(), r.getName(), c.done(), c.total());
                    })
                    .toList();
            statuses.put(s.getId(), new Status(completed(s, counts), required, blockedBy));
        }
        return statuses;
    }

    /** Matérias fora da distribuição automática: concluídas ou bloqueadas. */
    @Transactional(readOnly = true)
    public Set<UUID> onHold(UUID userId) {
        return statuses(userId).entrySet().stream()
                .filter(e -> e.getValue().onHold())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Troca as matérias de que esta depende. Precisam ser do usuário, diferentes dela e não podem
     * fechar um ciclo (A depende de B que depende de A).
     */
    @Transactional
    public void replace(UUID userId, UUID subjectId, List<UUID> requiredIds) {
        Set<UUID> wanted = new LinkedHashSet<>(requiredIds);
        if (wanted.contains(subjectId)) {
            throw new InvalidRequestException("prerequisiteIds", "a matéria não pode depender dela mesma");
        }
        Set<UUID> mine = subjects.findByUserIdOrderByPriorityOrderAsc(userId).stream().map(Subject::getId).collect(Collectors.toSet());
        if (!mine.containsAll(wanted)) {
            throw new InvalidRequestException("prerequisiteIds", "tem matéria que não existe ou não é sua");
        }
        Map<UUID, List<UUID>> graph = new HashMap<>(prerequisites(userId));
        graph.put(subjectId, new ArrayList<>(wanted));
        if (reaches(graph, wanted, subjectId)) {
            throw new InvalidRequestException("prerequisiteIds", "isso criaria um ciclo: uma matéria acabaria dependendo dela mesma");
        }
        jdbc.update("delete from subject_prerequisites where subject_id = ?", subjectId);
        wanted.forEach(required -> jdbc.update(
                "insert into subject_prerequisites (subject_id, required_subject_id) values (?, ?)", subjectId, required));
    }

    /** Matéria → as de que ela depende. */
    private Map<UUID, List<UUID>> prerequisites(UUID userId) {
        Map<UUID, List<UUID>> requires = new HashMap<>();
        jdbc.query("""
                select p.subject_id, p.required_subject_id
                from subject_prerequisites p join subjects s on s.id = p.subject_id
                where s.user_id = ?
                """, rs -> {
            requires.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
        }, userId);
        return requires;
    }

    /** Seguindo as dependências a partir de "from", chega em "target"? */
    private static boolean reaches(Map<UUID, List<UUID>> graph, Set<UUID> from, UUID target) {
        Deque<UUID> pending = new ArrayDeque<>(from);
        Set<UUID> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (current.equals(target)) {
                return true;
            }
            if (seen.add(current)) {
                pending.addAll(graph.getOrDefault(current, List.of()));
            }
        }
        return false;
    }

    private static boolean completed(Subject s, Map<UUID, PlannedCount> counts) {
        if (s.getCompletedAt() != null) {
            return true;
        }
        PlannedCount c = counts.getOrDefault(s.getId(), PlannedCount.NONE);
        return s.getLessonMode() == LessonMode.PLANNED && c.total() > 0 && c.done() == c.total();
    }
}
