package com.porganization.commitments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Um compromisso num dia. Para compromissos únicos, occurrenceDate é a própria data; para
 * recorrentes, cada dia da série vira uma ocorrência com o mesmo commitmentId.
 */
public record OccurrenceResponse(
        UUID commitmentId,
        LocalDate occurrenceDate,
        String title,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean allDay,
        boolean done,
        boolean recurring,
        String description,
        String location) {
}
