package com.porganization.notifications;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Um lembrete no corpo do compromisso: quantos minutos antes e por quais canais. */
public record ReminderSpec(
        @NotNull @Min(0) @Max(40320) Integer minutesBefore,
        @NotEmpty Set<ChannelType> channels) {
}
