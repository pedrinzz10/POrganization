package com.porganization.commitments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.porganization.commitments.Commitment;
import com.porganization.commitments.recurrence.RecurrenceRule;
import com.porganization.notifications.ReminderSpec;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CommitmentResponse(
        UUID id,
        String title,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean allDay,
        String description,
        String location,
        boolean done,
        RecurrenceRule recurrenceRule,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ReminderSpec> reminders) {

    public static CommitmentResponse from(Commitment c, List<ReminderSpec> reminders) {
        return new CommitmentResponse(c.getId(), c.getTitle(), c.getDate(), c.getStartTime(), c.getEndTime(),
                c.isAllDay(), c.getDescription(), c.getLocation(), c.isDone(), c.getRecurrenceRule(),
                c.getCreatedAt(), c.getUpdatedAt(), reminders);
    }
}
