package com.porganization.studies;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.studies.dto.SubjectRequest;
import com.porganization.studies.dto.SubjectResponse;
import com.porganization.studies.SubjectPrerequisiteService.Status;
import com.porganization.studies.dto.SubjectResponse.PlannedCount;
import com.porganization.studies.dto.TagResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matérias e tags. Devolve DTOs já montados, dentro da transação (as tags são lazy). */
@Service
public class SubjectService {

    private final SubjectRepository subjects;
    private final TagRepository tags;
    private final PlannedLessonService plannedLessons;
    private final SubjectPrerequisiteService prerequisites;
    private final Clock clock;

    public SubjectService(SubjectRepository subjects, TagRepository tags, PlannedLessonService plannedLessons,
            SubjectPrerequisiteService prerequisites, Clock clock) {
        this.subjects = subjects;
        this.tags = tags;
        this.plannedLessons = plannedLessons;
        this.prerequisites = prerequisites;
        this.clock = clock;
    }

    // ---------- matérias ----------

    @Transactional(readOnly = true)
    public List<SubjectResponse> list(UUID userId, String tagName, boolean includeArchived) {
        return subjects.findByUserIdOrderByPriorityOrderAsc(userId).stream()
                .filter(s -> includeArchived || !s.isArchived())
                .filter(s -> tagName == null || tagName.isBlank()
                        || s.getTags().stream().anyMatch(t -> t.getName().equalsIgnoreCase(tagName.trim())))
                .map(withCounts(userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(UUID userId, UUID id) {
        return withCounts(userId).apply(find(userId, id));
    }

    /** Matéria nova entra no fim da ordem de prioridade. */
    @Transactional
    public SubjectResponse create(UUID userId, SubjectRequest request) {
        Subject subject = new Subject(userId, request.name().trim(), subjects.maxPriorityOrder(userId) + 1);
        apply(userId, subject, request);
        subjects.save(subject);
        applyPrerequisites(userId, subject, request);
        return withCounts(userId).apply(subject);
    }

    @Transactional
    public SubjectResponse update(UUID userId, UUID id, SubjectRequest request) {
        Subject subject = find(userId, id);
        subject.setName(request.name().trim());
        apply(userId, subject, request);
        applyPrerequisites(userId, subject, request);
        return withCounts(userId).apply(subject);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        subjects.delete(find(userId, id));
    }

    /**
     * Grava priority_order 1..N na ordem recebida. A lista precisa ter exatamente as matérias não
     * arquivadas do usuário, sem repetir; as arquivadas vão para o fim, mantendo a ordem entre elas.
     */
    @Transactional
    public List<SubjectResponse> reorder(UUID userId, List<UUID> ids) {
        List<Subject> all = subjects.findByUserIdOrderByPriorityOrderAsc(userId);
        Map<UUID, Subject> active = all.stream().filter(s -> !s.isArchived())
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        if (new HashSet<>(ids).size() != ids.size()) {
            throw new InvalidRequestException("ids", "a lista tem matérias repetidas");
        }
        if (ids.size() != active.size() || !active.keySet().containsAll(ids)) {
            throw new InvalidRequestException("ids", "envie exatamente as suas matérias não arquivadas, sem faltar nenhuma");
        }

        int order = 1;
        for (UUID id : ids) {
            active.get(id).setPriorityOrder(order++);
        }
        for (Subject archived : all) {
            if (archived.isArchived()) {
                archived.setPriorityOrder(order++);
            }
        }
        return ids.stream().map(active::get).map(withCounts(userId)).toList();
    }

    /** Monta o DTO com a contagem das aulas definidas e os pré-requisitos (uma consulta para todas). */
    private Function<Subject, SubjectResponse> withCounts(UUID userId) {
        subjects.flush();
        Map<UUID, PlannedCount> counts = plannedLessons.counts(userId);
        Map<UUID, Status> statuses = prerequisites.statuses(userId);
        return s -> SubjectResponse.from(s, counts.getOrDefault(s.getId(), PlannedCount.NONE), statuses.get(s.getId()));
    }

    private void applyPrerequisites(UUID userId, Subject subject, SubjectRequest request) {
        if (request.prerequisiteIds() != null) {
            subjects.flush();
            prerequisites.replace(userId, subject.getId(), request.prerequisiteIds());
        }
    }

    private Subject find(UUID userId, UUID id) {
        return subjects.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Matéria não encontrada"));
    }

    private void apply(UUID userId, Subject subject, SubjectRequest request) {
        subject.setColor(request.color());
        if (request.sessionsPerWeek() != null) {
            subject.setSessionsPerWeek(request.sessionsPerWeek());
        }
        if (request.lessonMinutes() != null) {
            subject.setLessonMinutes(request.lessonMinutes());
        }
        if (request.archived() != null) {
            subject.setArchived(request.archived());
        }
        if (request.lessonMode() != null) {
            subject.setLessonMode(request.lessonMode());
        }
        if (request.completed() != null) {
            subject.setCompletedAt(request.completed()
                    ? (subject.getCompletedAt() != null ? subject.getCompletedAt() : OffsetDateTime.now(clock))
                    : null);
        }
        if (request.studyDays() != null) {
            subject.setStudyDays(request.studyDays());
        }
        if (request.tagIds() != null) {
            Set<UUID> wanted = new LinkedHashSet<>(request.tagIds());
            List<Tag> found = tags.findByUserIdAndIdIn(userId, wanted);
            if (found.size() != wanted.size()) {
                throw new InvalidRequestException("tagIds", "tem tag que não existe ou não é sua");
            }
            subject.getTags().clear();
            subject.getTags().addAll(found);
        }
    }

    // ---------- tags ----------

    @Transactional(readOnly = true)
    public List<TagResponse> listTags(UUID userId) {
        return tags.findByUserIdOrderByNameAsc(userId).stream().map(TagResponse::from).toList();
    }

    @Transactional
    public TagResponse createTag(UUID userId, String name) {
        String trimmed = name.trim();
        if (tags.findByUserIdAndNameIgnoreCase(userId, trimmed).isPresent()) {
            throw new ConflictException("Já existe uma tag com esse nome");
        }
        return TagResponse.from(tags.save(new Tag(userId, trimmed)));
    }

    @Transactional
    public TagResponse renameTag(UUID userId, UUID id, String name) {
        Tag tag = tags.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Tag não encontrada"));
        String trimmed = name.trim();
        tags.findByUserIdAndNameIgnoreCase(userId, trimmed).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Já existe uma tag com esse nome");
        });
        tag.setName(trimmed);
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteTag(UUID userId, UUID id) {
        tags.delete(tags.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Tag não encontrada")));
    }
}
