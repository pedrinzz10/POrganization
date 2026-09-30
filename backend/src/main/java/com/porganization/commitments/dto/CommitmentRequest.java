package com.porganization.commitments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.porganization.notifications.ReminderSpec;
import com.porganization.commitments.recurrence.RecurrenceRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Criação e edição. Na criação rápida só vêm title, date e startTime; sem startTime o
 * compromisso é de dia todo.
 */
public record CommitmentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        Boolean allDay,
        @Size(max = 2000) String description,
        @Size(max = 200) String location,
        RecurrenceRule recurrenceRule,
        // null = criação: lembrete padrão das configurações; edição: mantém os atuais. [] = sem lembrete.
        @Valid @Size(max = 5) List<ReminderSpec> reminders) {
}
