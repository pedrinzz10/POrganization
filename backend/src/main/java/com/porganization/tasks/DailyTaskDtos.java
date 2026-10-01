package com.porganization.tasks;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.porganization.commitments.recurrence.WeekDay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DailyTaskDtos {

    private DailyTaskDtos() {
    }

    /**
     * weekDays ausente = todo dia; lista vazia não vale (escolha pelo menos um dia).
     * reminderTime: horário do lembrete (HH:mm, fuso do usuário); ausente = sem lembrete.
     * timerMinutes: cronômetro que conclui a tarefa ao fim; ausente = sem cronômetro.
     */
    public record TaskRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 16) String emoji,
            Set<WeekDay> weekDays,
            @JsonFormat(pattern = "HH:mm") LocalTime reminderTime,
            @Min(1) @Max(240) Integer timerMinutes) {

        public TaskRequest(String title, String emoji, Set<WeekDay> weekDays) {
            this(title, emoji, weekDays, null, null);
        }

        public TaskRequest(String title, String emoji, Set<WeekDay> weekDays, LocalTime reminderTime) {
            this(title, emoji, weekDays, reminderTime, null);
        }
    }

    /** weekDays: os dias que valem hoje (a regra atual). */
    public record TaskResponse(UUID id, String title, String emoji, Set<WeekDay> weekDays, int position, boolean archived,
            LocalDate createdOn, @JsonFormat(pattern = "HH:mm") LocalTime reminderTime, Integer timerMinutes) {
    }

    public record ArchivePatch(@NotNull Boolean archived) {
    }

    public record OrderRequest(@NotEmpty List<UUID> ids) {
    }

    /** Sequência atual e % dos últimos 30 dias (null sem nenhum dia devido ainda). */
    public record TaskStatsResponse(UUID taskId, int streak, BigDecimal completionRate) {
    }

    /** Num dia: quantas tarefas ativas eram devidas e quantas foram feitas. */
    public record DayCount(LocalDate date, int due, int done) {
    }

    /** Uma tarefa devida no dia, com feito ou não; timerMinutes null = sem cronômetro. */
    public record DayTask(UUID id, String title, String emoji, int position, boolean done, Integer timerMinutes) {

        public DayTask(UUID id, String title, String emoji, int position, boolean done) {
            this(id, title, emoji, position, done, null);
        }
    }
}
