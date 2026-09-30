package com.porganization.studies.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Criação e edição de matéria; campos nulos usam o padrão (criação) ou mantêm o valor (edição). */
public record SubjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "use o formato #RRGGBB") String color,
        @Min(0) @Max(21) Integer sessionsPerWeek,
        @Min(5) @Max(240) Integer lessonMinutes,
        List<UUID> tagIds,
        Boolean archived) {
}
