package com.porganization.commitments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** Ajuste parcial de uma ocorrência: só os campos enviados mudam (null = mantém). */
public record OccurrencePatch(
        Boolean done,
        Boolean cancelled,
        @Size(max = 200) String title,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime) {
}
