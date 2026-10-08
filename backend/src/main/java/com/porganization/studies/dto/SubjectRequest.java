package com.porganization.studies.dto;

import com.porganization.commitments.recurrence.WeekDay;
import com.porganization.studies.LessonMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Criação e edição de matéria; campos nulos usam o padrão (criação) ou mantêm o valor (edição).
 * studyDays: dias em que a matéria pode ter aula; lista vazia = qualquer dia.
 * lessonMode: FREE ou PLANNED (aulas definidas, E14); nulo mantém (na criação, FREE).
 * prerequisiteIds: matérias de que esta depende (E17); completed: marcada como concluída. Nulos mantêm.
 */
public record SubjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "use o formato #RRGGBB") String color,
        @Min(0) @Max(21) Integer sessionsPerWeek,
        @Min(5) @Max(240) Integer lessonMinutes,
        List<UUID> tagIds,
        Boolean archived,
        Set<WeekDay> studyDays,
        LessonMode lessonMode,
        List<UUID> prerequisiteIds,
        Boolean completed) {

    public SubjectRequest(String name, String color, Integer sessionsPerWeek, Integer lessonMinutes, List<UUID> tagIds,
            Boolean archived) {
        this(name, color, sessionsPerWeek, lessonMinutes, tagIds, archived, null, null, null, null);
    }

    public SubjectRequest(String name, String color, Integer sessionsPerWeek, Integer lessonMinutes, List<UUID> tagIds,
            Boolean archived, Set<WeekDay> studyDays) {
        this(name, color, sessionsPerWeek, lessonMinutes, tagIds, archived, studyDays, null, null, null);
    }
}
