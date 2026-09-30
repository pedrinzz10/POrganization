package com.porganization.tasks;

import com.porganization.commitments.recurrence.WeekDay;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DailyTaskDtos {

    private DailyTaskDtos() {
    }

    /** weekDays ausente = todo dia; lista vazia não vale (escolha pelo menos um dia). */
    public record TaskRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 16) String emoji,
            Set<WeekDay> weekDays) {
    }

    /** weekDays: os dias que valem hoje (a regra atual). */
    public record TaskResponse(UUID id, String title, String emoji, Set<WeekDay> weekDays, int position, boolean archived,
            LocalDate createdOn) {
    }

    public record ArchivePatch(@NotNull Boolean archived) {
    }

    public record OrderRequest(@NotEmpty List<UUID> ids) {
    }

    /** Uma tarefa devida no dia, com feito ou não. */
    public record DayTask(UUID id, String title, String emoji, int position, boolean done) {
    }
}
